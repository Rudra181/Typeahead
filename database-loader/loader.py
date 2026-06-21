#!/usr/bin/env python3
import requests
import gzip
import csv
import psycopg2
import numpy as np
import datetime
import random
from datetime import timezone
import os
import time

URL = "https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-all-titles-in-ns0.gz"
CSV_FILE = "dataset.csv"
TARGET_ROWS = 1000000
DB_HOST = os.getenv("DB_HOST", "localhost")

def init_db_and_check():
    print(f"Connecting to Postgres at {DB_HOST}...")
    for _ in range(30):
        try:
            conn = psycopg2.connect(
                host=DB_HOST,
                database="typeahead",
                user="typeahead",
                password="typeahead",
                port=5432
            )
            cur = conn.cursor()
            
            # Ensure table exists so we can query row count
            cur.execute("""
            CREATE TABLE IF NOT EXISTS search_queries (
                query_text TEXT PRIMARY KEY,
                total_count BIGINT NOT NULL DEFAULT 0,
                recent_count_24h BIGINT NOT NULL DEFAULT 0,
                last_searched_at TIMESTAMPTZ,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
            );
            """)
            
            cur.execute("SELECT count(*) FROM search_queries;")
            count = cur.fetchone()[0]
            conn.commit()
            return conn, cur, count
        except psycopg2.OperationalError:
            time.sleep(2)
    raise Exception("Could not connect to database after 60 seconds")

def download_and_extract():
    print(f"Streaming data from {URL} (in-memory)...")
    titles = []
    
    response = requests.get(URL, stream=True)
    response.raise_for_status()
    
    with gzip.open(response.raw, "rt", encoding="utf-8") as f:
        next(f, None) # skip header
        line_count = 0
        for line in f:
            if len(titles) >= TARGET_ROWS:
                break
            line_count += 1
            if line_count % 15 != 0:
                continue
            t = line.strip()
            
            if len(t) > 60: continue
            if '(' in t or ')' in t: continue
            if all(c == '_' for c in t): continue
            if not t or not t[0].isalpha(): continue
            
            t = t.replace('_', ' ')
            titles.append(t)
    return titles

def build_computational_data(titles):
    print(f"Computing Zipf distributions for {len(titles)} titles...")
    np.random.seed(42)
    
    raw = np.random.zipf(a=1.07, size=len(titles))
    total_counts = np.clip(raw, 1, 200000)
    total_counts.sort()
    total_counts = total_counts[::-1] 
    
    print("Engineering trending queries logic...")
    dataset = []
    now = datetime.datetime.now(timezone.utc)
    
    for i, title in enumerate(titles):
        total_count = int(total_counts[i])
        
        if i >= len(titles) - 2000:
            recent_count = random.randint(50, 500)
            last_searched = now - datetime.timedelta(minutes=random.randint(5, 180))
        else:
            recent_count = 0
            days_ago = random.randint(1, 30)
            last_searched = now - datetime.timedelta(days=days_ago, minutes=random.randint(0, 1440))
            
        dataset.append([title, total_count, recent_count, last_searched.isoformat()])
        
    print("Writing records to CSV...")
    with open(CSV_FILE, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["query_text", "total_count", "recent_count_24h", "last_searched_at"])
        writer.writerows(dataset)

def load_to_postgres(conn, cur):
    print("Loading data via COPY into PostgreSQL...")
    cur.execute("TRUNCATE TABLE search_queries;")
    
    with open(CSV_FILE, "r", encoding="utf-8") as f:
        next(f)
        cur.copy_expert("COPY search_queries(query_text, total_count, recent_count_24h, last_searched_at) FROM STDIN WITH CSV", f)
        
    conn.commit()
    cur.close()
    conn.close()
    print("Successfully bulk loaded rows to database!")
    if os.path.exists(CSV_FILE):
        os.remove(CSV_FILE)

if __name__ == '__main__':
    conn, cur, count = init_db_and_check()
    if count > 0:
        print(f"Database already populated with {count} rows. Skipping load.")
        cur.close()
        conn.close()
    else:
        print("Database is empty. Starting processing pipeline...")
        titles = download_and_extract()
        build_computational_data(titles)
        load_to_postgres(conn, cur)

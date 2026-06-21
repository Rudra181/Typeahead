import { useEffect, useId, useRef, useState } from "react";
import { LoaderCircle, Search, TrendingUp } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import useDebouncedValue from "@/hooks/useDebouncedValue";
import { fetchSuggestions, submitSearch, fetchTrending } from "@/api/typeaheadApi";
import "./App.css";

export default function App() {
  const id = useId();
  const [value, setValue] = useState("");
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [trending, setTrending] = useState([]);
  const wrapperRef = useRef(null);

  const debounced = useDebouncedValue(value, 250);

  // Load the trending list on mount; close dropdown on outside click.
  useEffect(() => {
    fetchTrending()
      .then((data) => setTrending(Array.isArray(data) ? data : []))
      .catch(() => {});

    const onClickOutside = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  // Fetch suggestions whenever the debounced query changes.
  useEffect(() => {
    setActiveIndex(-1);
    const q = debounced.trim();
    if (!q) {
      setSuggestions([]);
      setLoading(false);
      setError(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    fetchSuggestions(q)
      .then((data) => {
        if (cancelled) return;
        setSuggestions(data.suggestions || []);
        setError(false);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [debounced]);

  const select = (query) => {
    setValue(query);
    setOpen(false);
    setSuggestions([]);
    // Record the search, then refresh the trending list so it stays live.
    submitSearch(query)
      .then(() => fetchTrending())
      .then((data) => setTrending(Array.isArray(data) ? data : []))
      .catch(() => {});
  };

  const onKeyDown = (e) => {
    if (!open) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIndex((i) => (i < suggestions.length - 1 ? i + 1 : i));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((i) => (i > 0 ? i - 1 : -1));
    } else if (e.key === "Enter") {
      if (activeIndex >= 0 && activeIndex < suggestions.length) {
        e.preventDefault();
        select(suggestions[activeIndex].query);
      }
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  };

  const showDropdown = open && value.trim().length > 0;

  return (
    <div className="page">
      <h1 className="title">Typeahead</h1>

      <div className="search" ref={wrapperRef}>
        <Label htmlFor={id} className="sr-only">
          Search
        </Label>

        <div className="relative">
          <Input
            id={id}
            className="peer pe-3 ps-9"
            placeholder="Search…"
            type="search"
            value={value}
            autoComplete="off"
            spellCheck="false"
            aria-autocomplete="list"
            aria-expanded={showDropdown}
            onChange={(e) => {
              setValue(e.target.value);
              setOpen(true);
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={onKeyDown}
          />

          {/* Left adornment: spinner while loading, search icon otherwise. */}
          <div className="pointer-events-none absolute inset-y-0 start-0 flex items-center justify-center ps-3 text-muted-foreground/80 peer-disabled:opacity-50">
            {loading ? (
              <LoaderCircle
                className="animate-spin"
                size={16}
                strokeWidth={2}
                role="status"
                aria-label="Loading..."
              />
            ) : (
              <Search size={16} strokeWidth={2} aria-hidden="true" />
            )}
          </div>
        </div>

        {showDropdown && (
          <div className="dropdown" role="listbox" aria-label="Search suggestions">
            {error && <div className="hint error">Couldn't load suggestions</div>}
            {!error && !loading && suggestions.length === 0 && (
              <div className="hint">No matches found</div>
            )}
            {!error &&
              suggestions.map((item, i) => (
                <div
                  key={item.query}
                  className={`option ${i === activeIndex ? "active" : ""}`}
                  role="option"
                  aria-selected={i === activeIndex}
                  onMouseEnter={() => setActiveIndex(i)}
                  onClick={() => select(item.query)}
                >
                  <Search size={15} strokeWidth={2} aria-hidden="true" className="option-icon" />
                  <span className="option-text">{item.query}</span>
                  {typeof item.totalCount === "number" && (
                    <span className="option-count">
                      {item.totalCount.toLocaleString()}
                    </span>
                  )}
                </div>
              ))}
          </div>
        )}
      </div>

      {trending.length > 0 && (
        <div className="trending">
          <div className="trending-title">
            <TrendingUp size={14} strokeWidth={2} aria-hidden="true" />
            Trending
          </div>
          <div className="trending-chips">
            {trending.map((t) => (
              <button
                key={t.query}
                type="button"
                className="chip"
                onClick={() => select(t.query)}
              >
                {t.query}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

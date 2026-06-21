const API_BASE = '/api';

export const fetchSuggestions = async (prefix, mode = 'trending') => {
  if (!prefix) return { suggestions: [] };
  const res = await fetch(`${API_BASE}/suggest?q=${encodeURIComponent(prefix)}&mode=${mode}`);
  if (!res.ok) throw new Error('Network response was not ok');
  return res.json();
};

export const submitSearch = async (query) => {
  const res = await fetch(`${API_BASE}/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query })
  });
  return res.json();
};

export const fetchTrending = async () => {
  const res = await fetch(`${API_BASE}/trending`);
  if (!res.ok) throw new Error('Network response was not ok');
  return res.json();
};

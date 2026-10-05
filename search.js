import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

export const options = { vus: 20, duration: '60s' };
const resultCount = new Trend('result_count');

const WINDOW_HOURS = Number(__ENV.WINDOW_HOURS || 168);   // 7 days; keep the same in both runs
const fmt = (d) => d.toISOString().slice(0, 19);         // UTC, no 'Z'

export default function () {
  // pickup/drop vary slightly so each request is different, but stay in the seeded city area
  const body = JSON.stringify({
    pickupLat: 28.55 + (Math.random() - 0.5) * 0.10,
    pickupLng: 77.10 + (Math.random() - 0.5) * 0.10,
    dropLat:   28.47 + (Math.random() - 0.5) * 0.10,
    dropLng:   77.04 + (Math.random() - 0.5) * 0.10,
    earliestTime: fmt(new Date()),
    latestTime:   fmt(new Date(Date.now() + WINDOW_HOURS * 3600 * 1000)),
  });

  const res = http.post('http://localhost:8080/api/v1/ride-offers/search', body, {
    headers: { 'Content-Type': 'application/json',
               Authorization: `Bearer ${__ENV.TOKEN}` },
  });

  const ok = check(res, { 'status 200': (r) => r.status === 200 });
  if (ok) resultCount.add(res.json().length);
}
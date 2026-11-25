import '@testing-library/jest-dom/vitest';

// O jsdom não implementa EventSource (SSE). Os testes não precisam do stream, só de que ele exista.
class EventSourceFalso {
  addEventListener() {}
  removeEventListener() {}
  close() {}
}
(globalThis as unknown as { EventSource: unknown }).EventSource = EventSourceFalso;

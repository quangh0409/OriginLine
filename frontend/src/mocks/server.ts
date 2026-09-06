import { setupServer } from "msw/node";
import { handlers } from "./handlers";

/**
 * Node-side MSW instance for SSR/server-component fetches and for any future
 * test runner (Vitest/Jest) that exercises src/lib/api against mocks without
 * a browser. Not wired into the app yet in this sprint — the app currently
 * only starts the browser worker (see src/mocks/msw-provider.tsx). Import
 * and call server.listen()/close() from test setup once F-sprint tests are
 * added.
 */
export const server = setupServer(...handlers);

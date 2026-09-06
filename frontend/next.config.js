const createNextIntlPlugin = require("next-intl/plugin");
const withPWA = require("@ducanh2912/next-pwa").default;

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

/** @type {import('next').NextConfig} */
const baseConfig = {
  reactStrictMode: true,
};

const withPwaWrapped = withPWA({
  dest: "public",
  cacheOnFrontEndNav: true,
  reloadOnOnline: true,
  // Custom service worker source (Web Push `push`/`notificationclick`
  // handlers) lives in worker/index.ts and gets merged into the generated
  // public/sw.js at build time. See worker/index.ts.
  customWorkerDir: "worker",
  // Service worker is noisy/unhelpful in local dev (constant recompiles);
  // enable it in production builds and whenever explicitly requested.
  disable:
    process.env.NODE_ENV === "development" &&
    process.env.NEXT_PUBLIC_ENABLE_PWA_DEV !== "true",
  workboxOptions: {
    disableDevLogs: true,
  },
});

module.exports = withPwaWrapped(withNextIntl(baseConfig));

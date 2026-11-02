// Web build config — the owner's public Render proxy endpoints.
// No secrets exist client-side in this build: the proxies hold the Gemini
// keys server-side. PREMIUM_PROXY is committed for a possible future lane
// only — default OFF, intentionally no caller anywhere in the web build.
window.WEB_CONFIG = {
  GEMINI_PROXY: "https://backrooms-gemini-proxy.onrender.com",
  PREMIUM_PROXY: "https://backrooms-premium-proxy.onrender.com"
};

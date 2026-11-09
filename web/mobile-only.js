/* Web-only access policy. Viewport width is not a device classifier: a
 * phone in landscape stays supported and a narrow desktop stays blocked. */
(function () {
  var ua = navigator.userAgent;
  var mobile = navigator.userAgentData && typeof navigator.userAgentData.mobile === "boolean"
    ? navigator.userAgentData.mobile
    : /Android.*Mobile|iPhone|iPod|Windows Phone/i.test(ua);
  window.__MOBILE_WEB_ALLOWED__ = mobile;
  document.documentElement.dataset.mobileWeb = mobile ? "allowed" : "blocked";
})();

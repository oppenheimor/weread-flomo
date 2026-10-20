// Run before the app/styles render to avoid a light flash on dark-mode startup.
(function () {
  var preference = 'system';
  try {
    var saved = localStorage.getItem('shimo-theme');
    if (saved === 'light' || saved === 'dark') preference = saved;
  } catch (_) {}
  document.documentElement.dataset.theme = preference === 'system'
    ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light') : preference;
})();

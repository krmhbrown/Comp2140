// Opens and closes the menu on small screens.
(function () {
  var button = document.querySelector('.nav-toggle');
  var nav = document.getElementById('site-nav');
  if (!button || !nav) { return; }
  button.addEventListener('click', function () {
    var open = nav.classList.toggle('open');
    button.setAttribute('aria-expanded', open ? 'true' : 'false');
  });
})();

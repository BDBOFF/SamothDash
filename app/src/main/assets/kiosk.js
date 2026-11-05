// Samoth Dash : masque la barre latérale et la barre du haut de Home Assistant.
// Le CSS est injecté dans les shadow DOM, et réappliqué quand HA reconstruit ses composants.
(function (cfg) {
  if (window.__samothKiosk) { window.__samothKiosk(cfg); return; }
  var ID = 'samoth-kiosk-style';
  var current = cfg;

  function setStyle(root, css) {
    if (!root) return;
    var el = root.getElementById ? root.getElementById(ID) : root.querySelector('#' + ID);
    if (!css) { if (el) el.remove(); return; }
    if (!el) { el = document.createElement('style'); el.id = ID; root.appendChild(el); }
    if (el.textContent !== css) el.textContent = css;
  }

  var DRAWER_CSS =
    'aside.mdc-drawer{display:none!important}' +
    ':host{--mdc-drawer-width:0px!important}' +
    '.mdc-drawer-app-content{margin:0!important;padding:0!important;width:100%!important}' +
    '.mdc-drawer-scrim{display:none!important}';
  var HEADER_CSS =
    ':host{--header-height:0px!important}' +
    '.header{display:none!important}' +
    '#view,hui-view-container{padding-top:0!important;min-height:100vh!important}';

  // Parcourt les shadow DOM : le nom du panneau parent de hui-root varie selon les versions de HA.
  function apply(root, depth) {
    var all = root.querySelectorAll('*');
    for (var i = 0; i < all.length; i++) {
      var e = all[i];
      if (!e.shadowRoot) continue;
      var n = e.tagName;
      if (n === 'HA-DRAWER') setStyle(e.shadowRoot, current.sidebar ? DRAWER_CSS : '');
      if (n === 'HUI-ROOT') {
        setStyle(e.shadowRoot, current.header ? HEADER_CSS : '');
        // Le panneau parent place aussi des éléments sous la barre du haut
        var panel = e.getRootNode().host;
        if (panel) {
          if (current.header) panel.style.setProperty('--header-height', '0px');
          else panel.style.removeProperty('--header-height');
        }
      }
      // Inutile de descendre dans les cartes
      if (n === 'HUI-ROOT' || n === 'HA-SIDEBAR' || depth > 8) continue;
      apply(e.shadowRoot, depth + 1);
    }
  }

  function tick() { try { apply(document, 0); } catch (err) {} }
  window.__samothKiosk = function (c) { current = c; tick(); };
  tick();
  setInterval(tick, 1000);
})(__CFG__);

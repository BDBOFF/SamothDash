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
    // HA récents : .sidebar-shell / .app-content. HA plus anciens : .mdc-drawer.
    ':host{--mdc-drawer-width:0px!important;--ha-sidebar-width:0px!important}' +
    '.sidebar-shell,wa-drawer{display:none!important}' +
    '.app-content{padding:0!important;width:100%!important}' +
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

  // --- Alerte plein écran : binary_sensor.X_person_occupancy (Frigate) + camera.X ---
  var occupancy = null;
  var watched = null;
  var overlay = null;
  var closeTimer = null;

  function dash(method, arg) {
    try { if (window.samothDash) window.samothDash[method](arg); } catch (err) {}
  }

  function closeAlert() {
    if (closeTimer) clearTimeout(closeTimer);
    closeTimer = null;
    if (!overlay) return;
    overlay.querySelector('img').src = ''; // coupe le flux
    overlay.remove();
    overlay = null;
    dash('alertState', false);
  }

  function showAlert(name, state) {
    closeAlert();
    overlay = document.createElement('div');
    overlay.id = 'samoth-alert';
    overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;z-index:2147483647;background:#000;';
    var img = document.createElement('img');
    img.style.cssText = 'width:100%;height:100%;object-fit:contain;display:block;';
    img.src = '/api/camera_proxy_stream/camera.' + name + '?token=' + encodeURIComponent(state.attributes.access_token || '');
    var tag = document.createElement('div');
    tag.textContent = 'Personne détectée · ' + (state.attributes.friendly_name || name);
    tag.style.cssText = 'position:absolute;top:20px;left:20px;padding:10px 18px;border-radius:99px;' +
      'background:#D10A20;color:#fff;font:600 18px sans-serif;';
    overlay.appendChild(img);
    overlay.appendChild(tag);
    overlay.addEventListener('click', closeAlert);
    document.body.appendChild(overlay);
    dash('alertState', true);
    dash('log', 'alerte ' + name);
    closeTimer = setTimeout(closeAlert, 30000);
  }

  function checkAlerts() {
    var ha = document.querySelector('home-assistant');
    var states = ha && ha.hass && ha.hass.states;
    if (!states) return;
    var next = {};
    var trigger = null;
    for (var id in states) {
      var m = /^binary_sensor\.(.+)_person_occupancy$/.exec(id);
      if (!m || !states['camera.' + m[1]]) continue;
      next[m[1]] = states[id].state;
      if (occupancy && occupancy[m[1]] !== 'on' && states[id].state === 'on') trigger = m[1];
    }
    var names = Object.keys(next).sort().join(', ');
    if (names !== watched) { watched = names; dash('alertInfo', names); }
    // Vu par l'appli, même si l'alerte est coupée : sert au diagnostic dans les réglages
    if (trigger) dash('detection', trigger);
    occupancy = next; // au chargement, une détection déjà en cours ne déclenche rien
    if (trigger && current.alert) showAlert(trigger, states['camera.' + trigger]);
    if (!current.alert) closeAlert();
  }

  // --- Flux figés : vidéo qui ne progresse plus pendant une minute ---
  var positions = new Map();
  var ticks = 0;

  function videos(root, out, depth) {
    var all = root.querySelectorAll('*');
    for (var i = 0; i < all.length; i++) {
      if (all[i].tagName === 'VIDEO') out.push(all[i]);
      if (all[i].shadowRoot && depth < 25) videos(all[i].shadowRoot, out, depth + 1);
    }
    return out;
  }

  function checkVideos() {
    if (!current.watchdog || overlay) return;
    var seen = new Map();
    var stuck = false;
    videos(document, [], 0).forEach(function (v) {
      if (v.paused || v.ended) return;
      var old = positions.get(v);
      var count = old && old.t === v.currentTime ? old.n + 1 : 0;
      seen.set(v, { t: v.currentTime, n: count });
      if (count >= 4) stuck = true;
    });
    positions = seen;
    if (stuck) { positions = new Map(); dash('videoStuck'); }
  }

  function tick() {
    try { apply(document, 0); } catch (err) {}
    try { checkAlerts(); } catch (err) {}
    if (++ticks % 15 === 0) { try { checkVideos(); } catch (err) {} }
  }
  window.__samothKiosk = function (c) { current = c; tick(); };
  // Bouton « Tester l'alerte » : affiche la première caméra suivie
  window.__samothTestAlert = function () {
    var ha = document.querySelector('home-assistant');
    var states = ha && ha.hass && ha.hass.states;
    var name = occupancy && Object.keys(occupancy).sort()[0];
    if (states && name) { showAlert(name, states['camera.' + name]); return true; }
    return false;
  };
  tick();
  setInterval(tick, 1000);
})(__CFG__);

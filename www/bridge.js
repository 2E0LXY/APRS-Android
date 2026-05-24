// ===========================================================================
//  APRS Net Android - bridge.js
//  Capacitor launch screen + native bridge. Mirrors the desktop client:
//   - persists settings (callsign, member account, appearance prefs)
//   - obtains GPS fixes from Android location services
//   - hands off to the live aprsnet.uk site with the client overlay
// ===========================================================================
(function () {
  'use strict';

  // Capacitor plugin handles (loaded from the global Capacitor object)
  var Cap   = window.Capacitor || {};
  var Prefs = Cap.Plugins && Cap.Plugins.Preferences;
  var Geo   = Cap.Plugins && Cap.Plugins.Geolocation;
  var Notif = Cap.Plugins && Cap.Plugins.LocalNotifications;
  var AppP  = Cap.Plugins && Cap.Plugins.App;

  var SERVER_URL = 'https://www.aprsnet.uk';
  var SETTINGS_KEY = 'aprs-android-settings';

  var defaults = {
    callsign: '', passcode: '',
    memberUser: '', memberPass: '',
    autoConnect: true, autoMemberLogin: true, notifications: true,
    positionMode: 'gps', manualLat: null, manualLon: null, beaconToMap: true,
    prefTheme: 'dark', prefMapStyle: '', prefFilters: {},
    prefAutoFit: null, prefGhost: null, prefPropLines: null, prefWxRadar: null,
    prefsSynced: false
  };

  function $(id) { return document.getElementById(id); }
  function status(msg) { var s = $('status'); if (s) s.textContent = msg || ''; }

  // -- settings persistence (Capacitor Preferences) --------------------------
  async function loadSettings() {
    try {
      if (!Prefs) return Object.assign({}, defaults);
      var r = await Prefs.get({ key: SETTINGS_KEY });
      if (r && r.value) return Object.assign({}, defaults, JSON.parse(r.value));
    } catch (e) {}
    return Object.assign({}, defaults);
  }

  async function saveSettings(s) {
    try {
      if (Prefs) await Prefs.set({ key: SETTINGS_KEY, value: JSON.stringify(s) });
    } catch (e) {}
  }

  // -- GPS -------------------------------------------------------------------
  async function getGPS() {
    try {
      if (!Geo) return null;
      var perm = await Geo.checkPermissions();
      if (perm.location !== 'granted') {
        var req = await Geo.requestPermissions();
        if (req.location !== 'granted') return null;
      }
      var pos = await Geo.getCurrentPosition({ enableHighAccuracy: true, timeout: 10000 });
      return { lat: pos.coords.latitude, lon: pos.coords.longitude,
               accuracy: pos.coords.accuracy };
    } catch (e) { return null; }
  }

  // -- the native API exposed to the website overlay -------------------------
  // Mirrors the desktop preload.js "aprsClient" object so the SAME
  // client-overlay.js works unmodified on Android.
  function installNativeBridge(settings) {
    window.aprsClient = {
      getSettings: function () { return Promise.resolve(settings); },
      saveSettings: function (patch) {
        settings = Object.assign({}, settings, patch);
        return saveSettings(settings).then(function () { return settings; });
      },
      getServer: function () { return Promise.resolve({ url: SERVER_URL }); },
      connectToServer: function () { window.location.href = SERVER_URL; },
      goBack: function () { window.location.href = 'index.html'; },
      probeServer: function () {
        return fetch(SERVER_URL + '/api/version')
          .then(function (r) { return r.json(); })
          .then(function (d) { return { ok: true, data: d }; })
          .catch(function () { return { ok: false }; });
      },
      showNotification: function (title, body) {
        if (!Notif || settings.notifications === false) return Promise.resolve();
        return Notif.schedule({ notifications: [{
          title: title || 'APRS Net', body: body || '',
          id: Date.now() % 100000, schedule: { at: new Date(Date.now() + 100) }
        }]}).catch(function () {});
      },
      getVersion: function () { return Promise.resolve('1.0.0'); },
      openExternal: function (url) { window.open(url, '_blank'); },
      reloadWindow: function () { window.location.reload(); },
      getPosition: function () {
        if (settings.positionMode === 'manual' &&
            typeof settings.manualLat === 'number') {
          return Promise.resolve({ lat: settings.manualLat, lon: settings.manualLon,
                                   source: 'manual' });
        }
        if (settings.positionMode === 'off') return Promise.resolve(null);
        return getGPS().then(function (fix) {
          return fix ? Object.assign({ source: 'gps' }, fix) : null;
        });
      },
      reportPosition: function () { return Promise.resolve(); },
      onSettings: function () {},
      platform: 'android'
    };
  }

  // -- launch flow -----------------------------------------------------------
  async function boot() {
    var settings = await loadSettings();
    installNativeBridge(settings);

    // Android hardware back button: from the site, return to launch screen;
    // from the launch screen, minimise the app.
    if (AppP) {
      AppP.addListener('backButton', function () {
        if (window.location.href.indexOf('aprsnet.uk') !== -1) {
          window.history.back();
        } else {
          AppP.minimizeApp && AppP.minimizeApp();
        }
      });
    }

    // Pre-warm the location permission so the map can plot the user quickly
    if (settings.positionMode === 'gps' && Geo) {
      Geo.requestPermissions().catch(function () {});
    }

    if (settings.autoConnect && settings.callsign) {
      status('Connecting as ' + settings.callsign + '...');
      setTimeout(function () { window.location.href = SERVER_URL; }, 700);
      return;
    }

    // Show the setup form, pre-filled
    $('loading').classList.add('hidden');
    $('setup').classList.remove('hidden');
    $('callsign').value   = settings.callsign || '';
    $('passcode').value   = settings.passcode || '';
    $('memberUser').value = settings.memberUser || '';
    $('memberPass').value = settings.memberPass || '';
    $('autoConnect').checked   = settings.autoConnect !== false;
    $('notifications').checked = settings.notifications !== false;
  }

  // called by the Connect button
  window.connect = async function () {
    var settings = await loadSettings();
    var patch = {
      callsign:   ($('callsign').value || '').trim().toUpperCase(),
      passcode:   ($('passcode').value || '').trim(),
      memberUser: ($('memberUser').value || '').trim(),
      memberPass: ($('memberPass').value || ''),
      autoConnect:   $('autoConnect').checked,
      notifications: $('notifications').checked
    };
    settings = Object.assign({}, settings, patch);
    await saveSettings(settings);
    installNativeBridge(settings);
    status('Opening APRS Net...');
    setTimeout(function () { window.location.href = SERVER_URL; }, 400);
  };

  document.addEventListener('DOMContentLoaded', boot);
})();
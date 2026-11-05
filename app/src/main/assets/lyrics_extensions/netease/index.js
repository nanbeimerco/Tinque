// NetEase Cloud Music: public search and lyric endpoints.
// Word-synced lyrics (yrc) are turned into enhanced LRC, and a translation,
// when there is one, follows each line as a second line with the same time
// stamp (which Tinque reads as that line's translation).

var HEADERS = {
  "User-Agent": "Mozilla/5.0",
  "Referer": "https://music.163.com/"
};

function pad(n, width) {
  var s = String(n);
  while (s.length < width) s = "0" + s;
  return s;
}

function stamp(ms) {
  if (ms < 0) ms = 0;
  var minutes = Math.floor(ms / 60000);
  var seconds = Math.floor((ms % 60000) / 1000);
  var hundredths = Math.floor((ms % 1000) / 10);
  return pad(minutes, 2) + ":" + pad(seconds, 2) + "." + pad(hundredths, 2);
}

// "[mm:ss.xx]text" lines into [{ time, text }], skipping metadata.
function parseLrc(text) {
  var out = [];
  if (!text) return out;
  var lines = text.split("\n");
  for (var i = 0; i < lines.length; i++) {
    var line = lines[i];
    if (!line || line.charAt(0) === "{") continue;
    var re = /\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g;
    var times = [];
    var m;
    var last = 0;
    while ((m = re.exec(line)) !== null) {
      var frac = m[3] ? parseInt((m[3] + "00").substring(0, 3), 10) : 0;
      times.push(parseInt(m[1], 10) * 60000 + parseInt(m[2], 10) * 1000 + frac);
      last = re.lastIndex;
    }
    if (times.length === 0) continue;
    var body = line.substring(last).trim();
    for (var t = 0; t < times.length; t++) out.push({ time: times[t], text: body });
  }
  out.sort(function (a, b) { return a.time - b.time; });
  return out;
}

// "[start,dur](wStart,wDur,0)word..." lines into enhanced LRC lines.
function yrcToLines(text) {
  var out = [];
  if (!text) return out;
  var lines = text.split("\n");
  for (var i = 0; i < lines.length; i++) {
    var line = lines[i];
    if (!line || line.charAt(0) === "{") continue;
    var head = /^\[(\d+),(\d+)\]/.exec(line);
    if (!head) continue;
    var start = parseInt(head[1], 10);
    var rest = line.substring(head[0].length);
    var re = /\((\d+),(\d+),\d+\)([^(]*)/g;
    var m;
    var body = "";
    var plain = "";
    while ((m = re.exec(rest)) !== null) {
      body += "<" + stamp(parseInt(m[1], 10)) + ">" + m[3];
      plain += m[3];
    }
    if (plain.trim().length === 0) continue;
    out.push({ time: start, text: body, plain: plain });
  }
  return out;
}

function lyricsFor(id) {
  return fetch("https://music.163.com/api/song/lyric/v1?id=" + id + "&lv=1&kv=1&tv=-1&yv=1&rv=-1&ytv=-1", { headers: HEADERS })
    .then(function (res) {
      if (!res.ok) return null;
      var data = res.json();
      var lrc = parseLrc(data && data.lrc && data.lrc.lyric);
      var words = yrcToLines(data && data.yrc && data.yrc.lyric);
      var translation = parseLrc(data && data.tlyric && data.tlyric.lyric);

      function translationAt(time) {
        var best = null;
        for (var i = 0; i < translation.length; i++) {
          var d = Math.abs(translation[i].time - time);
          if (d <= 600 && (best === null || d < best.d) && translation[i].text) best = { d: d, text: translation[i].text };
        }
        return best ? best.text : null;
      }

      var rows = [];
      var source = words.length > 0 ? words : lrc;
      var synced = source.some(function (l) { return l.time > 0; });
      for (var i = 0; i < source.length; i++) {
        var line = source[i];
        rows.push("[" + stamp(line.time) + "]" + line.text);
        var plainText = line.plain !== undefined ? line.plain : line.text;
        var tr = translation.length > 0 && plainText ? translationAt(line.time) : null;
        if (tr && tr !== plainText) rows.push("[" + stamp(line.time) + "]" + tr);
      }
      var syncedText = synced ? rows.join("\n") : null;
      var plainText = lrc.map(function (l) { return l.text; }).filter(function (t) { return t; }).join("\n");
      if (!syncedText && !plainText) return null;
      return { synced: syncedText, plain: plainText || null };
    })
    .catch(function () { return null; });
}

function search(title, artist, limit) {
  var keyword = (title + " " + (artist || "")).trim();
  var url = "https://music.163.com/api/cloudsearch/pc?s=" + encodeURIComponent(keyword) + "&type=1&limit=" + limit + "&offset=0";
  return fetch(url, { headers: HEADERS })
    .then(function (res) {
      if (!res.ok) return [];
      var data = res.json();
      var songs = data && data.result && data.result.songs;
      if (!Array.isArray(songs)) return [];
      return songs.map(function (s) {
        return {
          id: String(s.id),
          title: s.name || "",
          artist: (s.ar || []).map(function (a) { return a.name; }).join(", "),
          album: s.al ? s.al.name : "",
          durationMs: s.dt || 0
        };
      });
    })
    .catch(function () { return []; });
}

module.exports = {
  getLyrics: async function (track) {
    if (!track || !track.title) return null;
    var hits = await search(track.title, track.artist, 6);
    if (hits.length === 0) return null;
    var wanted = track.durationMs || 0;
    if (wanted > 0) {
      hits.sort(function (a, b) { return Math.abs(a.durationMs - wanted) - Math.abs(b.durationMs - wanted); });
      if (Math.abs(hits[0].durationMs - wanted) > 15000) return null;
    }
    var found = await lyricsFor(hits[0].id);
    if (!found) return null;
    return found.synced || found.plain;
  },

  searchLyrics: async function (query) {
    if (!query || !query.title) return [];
    var hits = await search(query.title, query.artist, 6);
    var results = [];
    for (var i = 0; i < hits.length && i < 5; i++) {
      var hit = hits[i];
      var found = await lyricsFor(hit.id);
      if (!found) continue;
      results.push({
        id: hit.id,
        title: hit.title,
        artist: hit.artist,
        album: hit.album,
        durationSeconds: Math.floor(hit.durationMs / 1000),
        provider: "NetEase Cloud Music",
        syncedLyrics: found.synced,
        plainLyrics: found.plain
      });
    }
    return results;
  }
};

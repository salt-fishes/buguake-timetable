/* 不挂科课表 官网 · 动画与交互（anime.js v4）
   设计依据：docs/superpowers/specs/2026-09-23-buguake-website-design.md §4
   规则：只动 transform/opacity；reduced-motion 或 anime 缺失时整体降级为静态可读 */
(function () {
  'use strict';
  window.__siteReady = true;

  var root = document.documentElement;
  var animOn = root.classList.contains('js-anim') && typeof window.anime !== 'undefined';

  // anime 加载失败：撤掉隐藏态，保证页面完整可读
  if (!animOn) root.classList.remove('js-anim');

  function clearTransform(el) { if (el) el.style.transform = ''; }
  function later(fn, ms) { setTimeout(fn, ms); }

  /* ---------- 移动端菜单 ---------- */
  var burger = document.getElementById('navBurger');
  var links = document.getElementById('navLinks');
  if (burger && links) {
    burger.addEventListener('click', function () {
      var open = links.classList.toggle('open');
      burger.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    links.addEventListener('click', function (e) {
      if (e.target.tagName === 'A') {
        links.classList.remove('open');
        burger.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /* ---------- Hero 入场时间线 ---------- */
  function heroEntrance() {
    var badge = document.getElementById('heroBadge');
    var phone = document.getElementById('heroPhone');
    var widget = document.getElementById('heroWidget');
    var door = document.getElementById('heroDoor');
    var titleLines = document.querySelectorAll('.hero-title .anim');
    var rest = document.querySelectorAll('.hero-desc, .hero-cta, .hero-chips');

    var tl = anime.timeline({ ease: 'out(3)', duration: 600 });
    if (badge) tl.add({
      targets: badge, opacity: [0, 1], translateY: [16, 0], rotate: ['-6deg', '-1.5deg']
    });
    if (titleLines.length) tl.add({
      targets: titleLines, opacity: [0, 1], translateY: [28, 0],
      delay: anime.stagger(80), duration: 550
    }, '-=320');
    if (rest.length) tl.add({
      targets: rest, opacity: [0, 1], translateY: [18, 0],
      delay: anime.stagger(70), duration: 550
    }, '-=380');
    if (phone) tl.add({
      targets: phone, opacity: [0, 1], translateY: [46, 0], rotate: ['6deg', '2deg'],
      duration: 850, ease: 'out(4)'
    }, '-=560');
    if (widget) tl.add({
      targets: widget, opacity: [0, 1], translateY: [-22, 0], rotate: ['-9deg', '-3deg'], duration: 650
    }, '-=420');
    if (door) tl.add({
      targets: door, opacity: [0, 1], translateX: [-26, 0], rotate: ['-9deg', '-2.5deg'], duration: 650
    }, '-=480');

    tl.complete(function () {
      [badge, phone, widget, door].forEach(clearTransform);
      document.querySelectorAll('.hero .anim').forEach(clearTransform);
    });

    // 数字 count-up：200+
    var counter = document.querySelector('[data-count]');
    if (counter) {
      var target = parseInt(counter.getAttribute('data-count'), 10) || 0;
      var obj = { v: 0 };
      counter.textContent = '0+';
      anime.animate(obj, {
        v: target, duration: 1500, delay: 500, ease: 'out(3)',
        onUpdate: function () { counter.textContent = Math.round(obj.v) + '+'; },
        onComplete: function () { counter.textContent = target + '+'; }
      });
    }
  }

  /* ---------- 滚动揭示（一次性） ---------- */
  function scrollReveal() {
    var hero = document.querySelector('.hero');
    var items = Array.prototype.filter.call(document.querySelectorAll('.anim'), function (el) {
      return !(hero && hero.contains(el));
    });
    if (!items.length) return;

    if (!('IntersectionObserver' in window)) {
      // 老浏览器兜底：直接显示
      items.forEach(function (el) { el.style.opacity = '1'; clearTransform(el); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var el = entry.target;
        io.unobserve(el);
        anime.animate(el, {
          opacity: [0, 1], translateY: [24, 0], rotate: ['-1.2deg', '0deg'],
          duration: 560, ease: 'out(3)'
        });
        later(function () { clearTransform(el); }, 640);
      });
    }, { threshold: 0.15, rootMargin: '0px 0px -30px 0px' });
    items.forEach(function (el) { io.observe(el); });
  }

  /* ---------- 更新日志时间线（changelog.html） ---------- */
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function renderChangelog() {
    var host = document.getElementById('timeline');
    if (!host) return;

    fetch('data/changelog.json')
      .then(function (r) { if (!r.ok) throw new Error('http ' + r.status); return r.json(); })
      .then(function (data) {
        var versions = (data && data.versions) || [];
        if (!versions.length) throw new Error('empty');

        host.innerHTML = versions.map(function (v) {
          var src = v.source === 'release'
            ? '<span class="tl-src">Releases</span>'
            : '<span class="tl-src">应用内记录</span>';
          var date = v.date ? '<span class="tl-date">' + escapeHtml(v.date) + '</span>' : '';
          var link = v.url
            ? '<a class="tl-link" href="' + escapeHtml(v.url) + '">本版本 ↗</a>'
            : '<a class="tl-link" href="https://github.com/salt-fishes/buguake-timetable/releases">Releases ↗</a>';
          var items = (v.items || []).map(function (t) {
            return '<li>' + escapeHtml(t) + '</li>';
          }).join('');
          return '<div class="tl-item' + (animOn ? ' anim' : '') + '">' +
            '<span class="tl-ver">v' + escapeHtml(v.version) + '</span>' +
            date + src +
            '<ul>' + items + '</ul>' + link +
            '</div>';
        }).join('');

        host.classList.add('drawn');

        if (animOn) {
          var els = host.querySelectorAll('.tl-item');
          anime.animate(els, {
            opacity: [0, 1], translateY: [18, 0],
            delay: anime.stagger(70), duration: 520, ease: 'out(3)'
          });
          later(function () { els.forEach(clearTransform); }, 70 * els.length + 620);
        }
      })
      .catch(function () {
        host.innerHTML = '<p class="tl-loading">更新记录加载失败——请直接访问 ' +
          '<a href="https://github.com/salt-fishes/buguake-timetable/releases" ' +
          'style="color:var(--brand);font-weight:800">GitHub Releases</a> 查看。</p>';
      });
  }

  /* ---------- 启动 ---------- */
  if (animOn) heroEntrance();
  scrollReveal();
  renderChangelog();
})();

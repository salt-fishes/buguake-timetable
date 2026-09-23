/* 不挂科课表 官网 · 动画与交互（anime.js v4.5）
   设计依据：docs/superpowers/specs/2026-09-23-buguake-website-design.md §4
   规则：只动 transform/opacity；reduced-motion 或 anime 缺失时整体降级为静态可读。
   注意：v4.5 的时间轴 API 是 anime.createTimeline()（v3 的 anime.timeline 已不存在）；
   任何一步异常都必须降级为「全部内容立即可见」，绝不能把页面卡在 opacity:0。 */
(function () {
  'use strict';
  window.__siteReady = true;

  var root = document.documentElement;
  var animOn = root.classList.contains('js-anim') && typeof window.anime !== 'undefined';

  // anime 加载失败：撤掉隐藏态，保证页面完整可读
  if (!animOn) root.classList.remove('js-anim');

  function clearTransform(el) { if (el) el.style.transform = ''; }
  function reveal(el) { if (el) { el.style.opacity = '1'; clearTransform(el); } }
  function later(fn, ms) { setTimeout(fn, ms); }

  // 终极降级：取消在跑的动画，把所有 .anim 强制显示，页面永远可读
  function failSafe(where, err) {
    animOn = false;
    root.classList.remove('js-anim');
    var els = document.querySelectorAll('.anim');
    try { if (window.anime && anime.remove) anime.remove(els); } catch (ignored) { }
    Array.prototype.forEach.call(els, reveal);
    if (window.console && console.warn) console.warn('[site] anim fallback @' + where + ':', err);
  }
  function attempt(where, fn) {
    if (!animOn && where !== 'changelog') return;
    try { fn(); } catch (e) { failSafe(where, e); }
  }

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

  /* ---------- Hero 入场时间线（v4: createTimeline，位置用绝对毫秒） ---------- */
  function heroEntrance() {
    var badge = document.getElementById('heroBadge');
    var phone = document.getElementById('heroPhone');
    var widget = document.getElementById('heroWidget');
    var door = document.getElementById('heroDoor');
    var titleLines = document.querySelectorAll('.hero-title .anim');
    var rest = document.querySelectorAll('.hero-desc, .hero-cta, .hero-chips');
    var counter = document.querySelector('[data-count]');
    if (!badge && !phone && !titleLines.length && !rest.length && !counter) return;

    var tl = anime.createTimeline({ defaults: { ease: 'out(3)', duration: 600 } });
    if (badge) tl.add({
      targets: badge, opacity: [0, 1], translateY: [16, 0], rotate: ['-6deg', '-1.5deg']
    }, 0);
    if (titleLines.length) tl.add({
      targets: titleLines, opacity: [0, 1], translateY: [28, 0],
      delay: anime.stagger(80), duration: 550
    }, 280);
    if (rest.length) tl.add({
      targets: rest, opacity: [0, 1], translateY: [18, 0],
      delay: anime.stagger(70), duration: 550
    }, 520);
    if (phone) tl.add({
      targets: phone, opacity: [0, 1], translateY: [46, 0], rotate: ['6deg', '2deg'],
      duration: 850, ease: 'out(4)'
    }, 560);
    if (widget) tl.add({
      targets: widget, opacity: [0, 1], translateY: [-22, 0], rotate: ['-9deg', '-3deg'],
      duration: 650
    }, 1000);
    if (door) tl.add({
      targets: door, opacity: [0, 1], translateX: [-26, 0], rotate: ['-9deg', '-2.5deg'],
      duration: 650
    }, 1100);

    var heroEls = [badge, phone, widget, door].concat(
      Array.prototype.slice.call(titleLines),
      Array.prototype.slice.call(rest)
    );
    tl.complete(function () {
      heroEls.forEach(reveal);
    });
    // 兜底：无论 complete 是否回调，约 2s 后强制显示
    later(function () { heroEls.forEach(reveal); }, 2200);

    // 数字 count-up：200+
    if (counter) {
      var target = parseInt(counter.getAttribute('data-count'), 10) || 0;
      var obj = { v: 0 };
      counter.textContent = '0+';
      anime.animate(obj, {
        v: target, duration: 1500, delay: 500, ease: 'out(3)',
        onUpdate: function () { counter.textContent = Math.round(obj.v) + '+'; },
        onComplete: function () { counter.textContent = target + '+'; }
      });
      later(function () { counter.textContent = target + '+'; }, 2600);
    }
  }

  /* ---------- 滚动揭示（一次性） ---------- */
  function scrollReveal() {
    var hero = document.querySelector('.hero');
    var items = Array.prototype.filter.call(document.querySelectorAll('.anim'), function (el) {
      return !(hero && hero.contains(el)) && el.style.opacity !== '1';
    });
    if (!items.length) return;

    if (!('IntersectionObserver' in window)) {
      items.forEach(reveal);
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var el = entry.target;
        io.unobserve(el);
        try {
          anime.animate(el, {
            opacity: [0, 1], translateY: [24, 0], rotate: ['-1.2deg', '0deg'],
            duration: 560, ease: 'out(3)'
          });
        } catch (e) {
          reveal(el);
          return;
        }
        // 兜底：动画无论如何在 700ms 内显示
        later(function () { reveal(el); }, 700);
      });
    }, { threshold: 0.15, rootMargin: '0px 0px -30px 0px' });
    items.forEach(function (el) { io.observe(el); });

    // 视口清扫兜底：部分 WebView 对 IO 的「首次相交通知」会延迟甚至吞掉（元素明明在首屏却永不触发），
    // 1.2s 后强制显示此刻已在视口内的元素；首屏之下的仍由滚动 IO 负责（滚动触发已验证可靠）
    later(function () {
      items.forEach(function (el) {
        if (el.style.opacity === '1') return;
        var r = el.getBoundingClientRect();
        if (r.top < innerHeight && r.bottom > 0) {
          try {
            anime.animate(el, {
              opacity: [0, 1], translateY: [24, 0],
              duration: 500, ease: 'out(3)'
            });
          } catch (e) { /* 动画失败也无所谓，直接显示 */ }
          later(function () { reveal(el); }, 600);
        }
      });
    }, 1200);
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

        var els = host.querySelectorAll('.tl-item');
        if (animOn) {
          try {
            anime.animate(els, {
              opacity: [0, 1], translateY: [18, 0],
              delay: anime.stagger(70), duration: 520, ease: 'out(3)'
            });
          } catch (e) { animOn = false; }
          // 兜底：无论如何按时显示
          later(function () {
            Array.prototype.forEach.call(els, reveal);
          }, 70 * els.length + 700);
        } else {
          Array.prototype.forEach.call(els, reveal);
        }
      })
      .catch(function () {
        host.innerHTML = '<p class="tl-loading">更新记录加载失败——请直接访问 ' +
          '<a href="https://github.com/salt-fishes/buguake-timetable/releases" ' +
          'style="color:var(--brand);font-weight:800">GitHub Releases</a> 查看。</p>';
      });
  }

  /* ---------- 启动（每步独立保护，任何失败都降级为全部可见） ---------- */
  attempt('hero', heroEntrance);
  attempt('reveal', scrollReveal);
  attempt('changelog', renderChangelog);
})();

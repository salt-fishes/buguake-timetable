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

  /* ---------- Hero 入场（分组 setTimeout + anime.animate） ----------
     不用 v4 的 Timeline API：4.5 上 createTimeline 的子动画不自动播放（实测静默两秒），
     已两次踩坑；anime.animate 是滚动揭示/数字动画验证过的可靠原语，用它手工编排顺序：
     徽章(0) → 标题(300) → 描述/按钮/徽章片(640) → 手机(700) → 小组件(1150) → 开门卡(1300) */
  function heroEntrance() {
    var badge = document.getElementById('heroBadge');
    var phone = document.getElementById('heroPhone');
    var widget = document.getElementById('heroWidget');
    var door = document.getElementById('heroDoor');
    var titleLines = document.querySelectorAll('.hero-title .anim');
    var rest = document.querySelectorAll('.hero-desc, .hero-cta, .hero-chips');
    var counter = document.querySelector('[data-count]');
    if (!badge && !phone && !titleLines.length && !rest.length && !counter) return;

    // 编排原语：delay 毫秒后启动一组动画，动画结束（或最迟 duration+700ms）强制显示
    function stage(delay, targets, params) {
      if (!targets || (targets.length === 0 && !targets.style) && !targets.getAttribute) return;
      var list = targets.length !== undefined && typeof targets !== 'string'
        ? Array.prototype.slice.call(targets) : [targets];
      list = list.filter(Boolean);
      if (!list.length) return;
      later(function () {
        try {
          anime.animate(Object.assign({ targets: list }, params));
        } catch (e) {
          list.forEach(reveal);
          return;
        }
        later(function () { list.forEach(reveal); }, (params.duration || 550) + 200);
      }, delay);
    }

    stage(0, badge, {
      opacity: [0, 1], translateY: [16, 0], rotate: ['-6deg', '-1.5deg'],
      duration: 600, ease: 'out(3)'
    });
    stage(300, titleLines, {
      opacity: [0, 1], translateY: [28, 0], duration: 550,
      delay: anime.stagger(90), ease: 'out(3)'
    });
    stage(640, rest, {
      opacity: [0, 1], translateY: [18, 0], duration: 520,
      delay: anime.stagger(80), ease: 'out(3)'
    });
    stage(700, phone, {
      opacity: [0, 1], translateY: [46, 0], rotate: ['6deg', '2deg'],
      duration: 850, ease: 'out(4)'
    });
    stage(1150, widget, {
      opacity: [0, 1], translateY: [-22, 0], rotate: ['-9deg', '-3deg'],
      duration: 650, ease: 'out(3)'
    });
    stage(1300, door, {
      opacity: [0, 1], translateX: [-26, 0], rotate: ['-9deg', '-2.5deg'],
      duration: 650, ease: 'out(3)'
    });

    // 全场兜底：约 2.4s 后无论哪一环出问题都强制显示
    var heroEls = [badge, phone, widget, door].concat(
      Array.prototype.slice.call(titleLines),
      Array.prototype.slice.call(rest)
    ).filter(Boolean);
    later(function () { heroEls.forEach(reveal); }, 2400);

    // 数字 count-up：200+（跟在描述文字之后，550ms 起跳）
    if (counter) {
      var target = parseInt(counter.getAttribute('data-count'), 10) || 0;
      var obj = { v: 0 };
      later(function () {
        counter.textContent = '0+';
        try {
          anime.animate(obj, {
            v: target, duration: 1400, ease: 'out(3)',
            onUpdate: function () { counter.textContent = Math.round(obj.v) + '+'; },
            onComplete: function () { counter.textContent = target + '+'; }
          });
        } catch (e) { counter.textContent = target + '+'; }
        later(function () { counter.textContent = target + '+'; }, 1700);
      }, 550);
      counter.textContent = '0+';
    }
  }

  /* ---------- 滚动揭示（scroll 事件轮询，不依赖 IntersectionObserver） ----------
     实测该环境下 IO 不可靠：首次相交通知被吞、滚动中还有漏触发，导致元素「直接冒出/一直隐身」。
     改为 scroll/resize + 定时补扫：元素顶边进入视口 90% 线即播入场动画（自上而下依次触发），
     每个元素只触发一次；动画失败或超时兜底强制显示。 */
  function scrollReveal() {
    var hero = document.querySelector('.hero');
    var pending = Array.prototype.filter.call(document.querySelectorAll('.anim'), function (el) {
      return !(hero && hero.contains(el));
    }).filter(function (el) { return el.style.opacity !== '1'; });
    if (!pending.length) return;

    function playEl(el, delay) {
      if (!el || el.getAttribute('data-revealed')) return;
      el.setAttribute('data-revealed', '1');      // 立即占位，防同帧重复触发
      later(function () {
        try {
          anime.animate(el, {
            opacity: [0, 1], translateY: [24, 0], rotate: ['-1.2deg', '0deg'],
            duration: 560, ease: 'out(3)'
          });
        } catch (e) { /* 播不了也无所谓，下面兜底会显示 */ }
        later(function () { reveal(el); }, 760);
      }, delay || 0);
    }

    function sweep() {
      if (!animOn) return;
      var vh = innerHeight;
      var rest = [];
      var batch = [];
      for (var i = 0; i < pending.length; i++) {
        var el = pending[i];
        if (el.getAttribute('data-revealed')) continue;
        var r = el.getBoundingClientRect();
        if (r.top < vh && r.bottom > 0) {
          batch.push(el);                      // 进入视口任意部分 → 入场动画
        } else if (r.bottom <= 0) {
          playEl(el, 0);                       // 被快速跳过也播（离屏进行，回滚可见尾段）
        } else {
          rest.push(el);                       // 视口外 → 继续等
        }
      }
      // 同一批按 DOM 顺序 70ms 级联，形成自上而下的依次入场
      batch.forEach(function (el, idx) { playEl(el, idx * 70); });
      pending = rest;
      if (!pending.length) {
        window.removeEventListener('scroll', onScroll);
        window.removeEventListener('resize', onScroll);
      }
    }

    var ticking = false;
    function onScroll() {
      if (ticking) return;
      ticking = true;
      (window.requestAnimationFrame || function (f) { setTimeout(f, 16); })(function () {
        ticking = false;
        sweep();
      });
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll, { passive: true });

    sweep();            // 首屏立即扫
    later(sweep, 600);  // 定时补扫（防滚动事件缺失/被节流）
    later(sweep, 1500);
    later(sweep, 3000);
    // 终极兜底：4s 后视口内/刚被跳过的还没入场 → 也走带动画的入场
    later(function () {
      pending.slice().forEach(function (el) {
        var r = el.getBoundingClientRect();
        if ((r.top < innerHeight && r.bottom > 0) || r.bottom <= 0) {
          playEl(el, 0);
        }
      });
    }, 4000);
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

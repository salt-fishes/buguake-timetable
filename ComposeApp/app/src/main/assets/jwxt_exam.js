/*
 * 正方教务（V9）考试安排抓取脚本 —— 校园本地化功能，非拾光适配器。
 *
 * 与课程导入一致的分工：脚本只负责"取回原始 JSON"，解析全部在原生 JwxtExamParser。
 *
 * 原生调用时序（每一步结果都经 window.CampusExamBridge.post 回传）：
 *   1) window.__campusExam.start()
 *        · 已在「考试信息查询」页 → {ok:true, kind:"terms", years:[…], semesters:[…], currentYear, currentSemester}
 *        · 在教务系统内但不在该页 → {ok:true, kind:"navigating", url:"/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105"}
 *          （原生据此 loadUrl 并在页面加载完成后再次调用 start()，实现"登录后一键"）
 *        · 尚未登录教务系统 → {ok:false, msg:"请先登录…"}
 *   2) window.__campusExam.read(xnm, xqm)
 *        → {ok:true, kind:"exams", data:"<原始查询响应>", xnm, xqm}
 *
 * 脚本会被反复注入（每次页面加载后），因此只挂 window 属性，不使用顶层 const/let。
 */
(function () {
    var BASE = '/kwgl/kscx_cxXsksxxIndex.html';
    var GN = 'N358105';
    var PAGE_SIZE = 200;
    var MAX_PAGES = 5;

    function isExamPage() {
        return location.pathname.indexOf('kscx_cxXsksxxIndex') >= 0;
    }

    function post(obj) {
        try {
            window.CampusExamBridge.post(JSON.stringify(obj));
        } catch (e) {
            console.warn('[CampusExam] 桥不可用', e);
        }
    }

    function text(v) {
        return String(v === null || v === undefined ? '' : v).trim();
    }

    /** 从页面菜单里找考试查询入口（V9 门户用 onclick="clickMenu('N358105','/kwgl/…')"）。 */
    function examUrlFromDom() {
        var nodes = document.querySelectorAll('[onclick],[href]');
        for (var i = 0; i < nodes.length; i++) {
            var raw = (nodes[i].getAttribute('onclick') || '') + ' ' +
                (nodes[i].getAttribute('href') || '');
            var m = raw.match(/['"](\/[^'"]*kscx_cxXsksxxIndex[^'"]*)['"]/);
            if (m) return m[1];
        }
        return '';
    }

    /** 从当前地址推出应用上下文前缀（兼容 /jwglxt/xtgl/… 这类部署）。 */
    function contextPrefix() {
        var m = location.pathname.match(/^(.*?)\/xtgl\//);
        return m ? m[1] : '';
    }

    /** 判断当前是否停在正方教务系统里（而不是统一身份认证页或其它站点）。 */
    function looksLikeJwxt() {
        if (isExamPage()) return true;
        if (location.pathname.indexOf('/xtgl/') >= 0) return true;
        if (examUrlFromDom()) return true;
        try {
            if (typeof window.clickMenu === 'function') return true;
        } catch (e) { /* ignore */ }
        return false;
    }

    /** 补成绝对地址：WebView.loadUrl 不接受相对路径（原生侧还有一层兜底）。 */
    function absolute(path) {
        if (/^[a-z][a-z0-9+.-]*:\/\//i.test(path)) return path;
        return location.origin + path;
    }

    /** 目标考试查询页地址：优先用页面菜单给出的真实路径，其次按上下文前缀拼默认路径。 */
    function locateExamUrl() {
        var fromDom = examUrlFromDom();
        if (fromDom) {
            return absolute(fromDom.indexOf('?') >= 0 ? fromDom : fromDom + '?gnmkdm=' + GN);
        }
        return location.origin + contextPrefix() + BASE + '?gnmkdm=' + GN;
    }

    function optionsOf(id) {
        var el = document.getElementById(id);
        if (!el || !el.options) return [];
        var out = [];
        for (var i = 0; i < el.options.length; i++) {
            out.push({ code: text(el.options[i].value), label: text(el.options[i].textContent) });
        }
        return out;
    }

    function selectedOf(id) {
        var el = document.getElementById(id);
        return el ? text(el.value) : '';
    }

    function queryBody(page, xnm, xqm) {
        var p = new URLSearchParams();
        p.set('_search', 'false');
        p.set('nd', String(Date.now()));
        p.set('queryModel.showCount', String(PAGE_SIZE));
        p.set('queryModel.currentPage', String(page));
        p.set('queryModel.sortName', '');
        p.set('queryModel.sortOrder', 'asc');
        p.set('time', '1');
        // 学期过滤参数就是 xnm/xqm（页面上下拉的 id 是 cx_xnm/cx_xqm，提交名不同）
        if (xnm) p.set('xnm', xnm);
        if (xqm) p.set('xqm', xqm);
        return p.toString();
    }

    function postQuery(page, xnm, xqm) {
        return fetch(BASE + '?doType=query&gnmkdm=' + GN, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest',
            },
            body: queryBody(page, xnm, xqm),
        });
    }

    function parseJson(body) {
        var t = String(body).replace(/^\uFEFF/, '').trim();
        if (t.charAt(0) !== '{') return null;
        try {
            return JSON.parse(t);
        } catch (e) {
            return null;
        }
    }

    async function query(xnm, xqm) {
        var first = parseJson(await (await postQuery(1, xnm, xqm)).text());
        if (!first) return null;
        var items = first.items || [];
        var total = first.totalResult || items.length;
        var page = 2;
        while (items.length < total && page <= MAX_PAGES) {
            var more = parseJson(await (await postQuery(page, xnm, xqm)).text());
            var moreItems = more && more.items ? more.items : [];
            if (moreItems.length === 0) break;
            items = items.concat(moreItems);
            page++;
        }
        return { items: items, totalResult: total };
    }

    /** 查询条件区的下拉是页面脚本后置渲染的：等它出现再读，避免回传空选项。 */
    function waitForSelects(cb, tries) {
        var left = tries === undefined ? 24 : tries;   // 24 × 250ms = 6s
        if (document.getElementById('cx_xnm') || document.getElementById('cx_xqm') || left <= 0) {
            cb();
            return;
        }
        setTimeout(function () { waitForSelects(cb, left - 1); }, 250);
    }

    window.__campusExam = {
        start: function () {
            if (!looksLikeJwxt()) {
                post({ ok: false, msg: '请先登录教务系统，再点「读取本页考试」' });
                return;
            }
            if (!isExamPage()) {
                // 让原生去导航：页面脚本跳转容易在 WebView 里丢失状态
                post({ ok: true, kind: 'navigating', url: locateExamUrl() });
                return;
            }
            waitForSelects(function () {
                post({
                    ok: true,
                    kind: 'terms',
                    years: optionsOf('cx_xnm'),
                    semesters: optionsOf('cx_xqm'),
                    currentYear: selectedOf('cx_xnm'),
                    currentSemester: selectedOf('cx_xqm'),
                });
            });
        },

        read: function (xnm, xqm) {
            query(xnm, xqm).then(function (data) {
                if (!data) {
                    post({ ok: false, msg: '未取到考试数据（登录可能已过期），请重新登录后再试' });
                    return;
                }
                post({
                    ok: true,
                    kind: 'exams',
                    data: JSON.stringify(data),
                    xnm: xnm || '',
                    xqm: xqm || '',
                });
            }).catch(function (e) {
                post({ ok: false, msg: '抓取失败：' + (e && e.message ? e.message : String(e)) });
            });
        },
    };
})();

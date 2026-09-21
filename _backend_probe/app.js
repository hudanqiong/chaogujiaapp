// ===== 模拟炒股练习 App 前端逻辑 =====
const API = location.origin + '/stock_api';
let TOKEN = localStorage.getItem('tk') || '';
let UNAME = localStorage.getItem('un') || '';
let CUR_TAB = 'index';
let CUR_STOCK = null;      // 当前详情页股票
let CUR_DIR = 'buy';       // 交易方向
let WATCH_CACHE = null;    // 自选列表缓存（避免重复请求闪烁）
let CUR_GID = 0;           // 当前选中的自选分组 id

// ---------- 工具 ----------
function $(id){ return document.getElementById(id); }
function toast(msg){
  const t = $('toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(window.__tt); window.__tt = setTimeout(()=>t.classList.remove('show'), 1800);
}
function esc(s){ return String(s==null?'':s).replace(/[&<>"]/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'"'}[c])); }
function num(v, d=2){ return Number(v||0).toFixed(d); }
function cls(p){ p = Number(p); return p>0?'red':(p<0?'green':''); }
function sign(p){ p = Number(p); return p>0?'+':''; }

async function api(path, data, method){
  const opt = { method: method || (data?'POST':'GET'), headers:{} };
  // token 三通道发送：① Authorization 头 ② URL 参数 ③ POST body
  // （因为 Apache 未透传 Authorization 头，后端 current_user() 已支持参数兜底读取）
  if(TOKEN) opt.headers['Authorization'] = 'Bearer ' + TOKEN;
  let url = API + '/' + path;
  if(TOKEN){
    url += (url.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(TOKEN);
  }
  if(data){
    opt.headers['Content-Type'] = 'application/json';
    if(TOKEN && data.token === undefined) data.token = TOKEN;
    opt.body = JSON.stringify(data);
  }else if(method === 'POST' && TOKEN){
    opt.headers['Content-Type'] = 'application/json';
    opt.body = JSON.stringify({ token: TOKEN });
  }
  try{
    const r = await fetch(url, opt);
    const j = await r.json();
    if(j.code === 401){ logout(true); throw new Error('登录已过期'); }
    return j;
  }catch(e){
    return { code:-1, msg: e.message || '网络错误' };
  }
}

// ===== 实时行情刷新引擎 =====
// 每 2 秒拉一次最新价，只更新价格文本/颜色，不重建 DOM（无闪烁）
let __rtTimer = null;
let __rtBusy  = false;
let __rtFail  = 0;                    // 连续失败次数

// 是否 A 股交易时段（周一~周五 9:30-11:30 / 13:00-15:00）
function __isTradeTime(){
  const now = new Date();
  const day = now.getDay();                        // 0=周日 6=周六
  if(day === 0 || day === 6) return false;
  const t = now.getHours() * 60 + now.getMinutes(); // 当日分钟数
  return (t >= 570 && t <= 690) || (t >= 780 && t <= 900);
}
// 计算下一轮间隔：交易时段 2 秒；非交易时段 30 秒；连续失败自动降频
function __rtInterval(){
  if(__rtFail >= 3) return 15000;                  // 连续失败 → 放慢到 15 秒（保护 IP）
  if(__rtFail >= 1) return 5000;                   // 偶发失败 → 5 秒
  return __isTradeTime() ? 2000 : 30000;           // 正常：交易时段 2 秒，休息时段 30 秒
}
function rtStart(){
  if(__rtTimer) return;
  __rtTimer = setInterval(rtTick, 2000);
}
function rtStop(){
  if(__rtTimer){ clearInterval(__rtTimer); __rtTimer = null; }
}
async function rtTick(){
  if(__rtBusy) return;
  // 只在首页/行情/自选/交易这几个有价格的页面刷新
  if(!['index','quote','watch','trade'].includes(CUR_TAB)) return;
  if(document.hidden) return;           // 页面切后台不刷
  if(CUR_STOCK) return;                 // 在个股详情页时不干扰
  // 自适应节流：距上次刷新不足当前间隔则跳过（实现动态频率）
  const iv = __rtInterval();
  const nowMs = Date.now();
  if(nowMs - (window.__rtLast || 0) < iv) return;
  window.__rtLast = nowMs;
  __rtBusy = true;
  try{
    const j = await api('stocks.php');
    if(j.code !== 0 || !Array.isArray(j.data)){ __rtFail++; return; }
    __rtFail = 0;                       // 成功则清零
    const map = {};
    j.data.forEach(s => { map[s.code] = s; });
    rtApply(map);
  }catch(e){ __rtFail++; }
  finally{ __rtBusy = false; }
}
function rtApply(map){
  // 1) 首页指数卡
  document.querySelectorAll('#idxCard .idx-item').forEach(el=>{
    const s = map[el.dataset.code]; if(!s) return;
    const v = el.querySelector('.v'), c = el.querySelector('.c');
    if(v){ v.textContent = num(s.price); v.className = 'v ' + cls(s.changePct); }
    if(c){ c.textContent = sign(s.changePct) + num(s.changePct) + '%'; c.className = 'c ' + cls(s.changePct); }
  });
  // 2) 自选列表行
  document.querySelectorAll('.wrow[data-code]').forEach(el=>{
    const s = map[el.dataset.code]; if(!s) return;
    const prc = el.querySelector('[data-prc]'), pct = el.querySelector('[data-pct]');
    if(prc){ prc.textContent = num(s.price); prc.className = 'prc ' + cls(s.changePct); }
    if(pct){
      pct.textContent = sign(s.changePct) + num(s.changePct) + '%';
      pct.className = 'pct ' + (s.changePct>0?'pct-up':(s.changePct<0?'pct-down':''));
    }
  });
  // 3) 交易页股票行
  document.querySelectorAll('.stock-row[data-code]').forEach(el=>{
    const s = map[el.dataset.code]; if(!s) return;
    const prc = el.querySelector('[data-prc]'), tag = el.querySelector('[data-pct]');
    if(prc){ prc.textContent = num(s.price); prc.className = 'price ' + cls(s.changePct); }
    if(tag){
      tag.textContent = sign(s.changePct) + num(s.changePct) + '%';
      tag.className = 'chg-tag ' + (s.changePct>0?'up':(s.changePct<0?'down':'flat'));
    }
  });
  // 4) 持仓卡片：现价/浮动盈亏随行情变（重算数值）
  rtApplyHolding(map);
}
// 持仓页重算：用最新价替换现价、市值、浮盈
function rtApplyHolding(map){
  if(CUR_TAB !== 'trade') return;
  document.querySelectorAll('.trade-box[data-code]').forEach(el=>{
    const code = el.dataset.code, s = map[code];
    if(!s) return;
    const qty = Number(el.dataset.qty || 0);
    const cost = Number(el.dataset.cost || 0);
    if(!qty || !cost) return;
    const price = Number(s.price);
    const profit = (price - cost) * qty;
    const profitPct = cost ? (price - cost) / cost * 100 : 0;
    const mv = price * qty;
    const priceEl = el.querySelector('[data-h-price]');
    if(priceEl) priceEl.textContent = num(cost) + ' / ' + num(price);
    const mvEl = el.querySelector('[data-h-mv]');
    if(mvEl) mvEl.textContent = '¥' + num(mv);
    const pfEl = el.querySelector('[data-h-profit]');
    if(pfEl){ pfEl.textContent = sign(profit) + num(profit); pfEl.className = cls(profit); }
    const ppEl = el.querySelector('[data-h-pct]');
    if(ppEl){ ppEl.textContent = sign(profitPct) + num(profitPct) + '%'; ppEl.className = cls(profit); }
  });
}

// ---------- 登录 / 注册 ----------
function switchAuth(which){
  const isLogin = which === 'login';
  $('tabLogin').classList.toggle('on', isLogin);
  $('tabReg').classList.toggle('on', !isLogin);
  $('loginForm').classList.toggle('hidden', !isLogin);
  $('regForm').classList.toggle('hidden', isLogin);
}
async function doLogin(){
  const u = $('lUser').value.trim(), p = $('lPass').value;
  if(!u || !p) return toast('请输入用户名和密码');
  toast('登录中…');
  const j = await api('login.php', { username:u, password:p });
  if(j.code === 0){
    TOKEN = j.data.token; UNAME = j.data.username || u;
    localStorage.setItem('tk', TOKEN); localStorage.setItem('un', UNAME);
    enterApp();
  }else toast(j.msg || '登录失败');
}
async function doRegister(){
  const u = $('rUser').value.trim(), p = $('rPass').value, p2 = $('rPass2').value;
  if(!u || !p) return toast('请填写用户名和密码');
  if(u.length < 3) return toast('用户名至少3位');
  if(p.length < 6) return toast('密码至少6位');
  if(p !== p2) return toast('两次密码不一致');
  toast('注册中…');
  const j = await api('register.php', { username:u, password:p });
  if(j.code === 0){
    toast('注册成功，正在登录…');
    const j2 = await api('login.php', { username:u, password:p });
    if(j2.code === 0){
      TOKEN = j2.data.token; UNAME = j2.data.username || u;
      localStorage.setItem('tk', TOKEN); localStorage.setItem('un', UNAME);
      enterApp();
    }
  }else toast(j.msg || '注册失败');
}
function logout(silent){
  rtStop();           // 停止实时行情刷新
  TOKEN=''; UNAME='';
  localStorage.removeItem('tk'); localStorage.removeItem('un');
  $('authPage').classList.remove('hidden');
  $('mainApp').classList.add('hidden');
  if(!silent) toast('已退出');
}
function enterApp(){
  $('authPage').classList.add('hidden');
  $('mainApp').classList.remove('hidden');
  switchTab('index');
  rtStart();          // 启动实时行情刷新
}

// ---------- Tab 切换 ----------
function switchTab(tab){
  CUR_TAB = tab; CUR_STOCK = null;
  document.querySelectorAll('.tabbar .tab').forEach(el=>{
    el.classList.toggle('active', el.dataset.tab === tab);
  });
  syncTabIcon();
  const titles = { index:'我的', quote:'行情', watch:'自选', trade:'交易', news:'资讯' };
  $('topTitle').textContent = titles[tab] || '';
  $('topBack').textContent = '';
  $('topAct').innerHTML = tab==='index' ? '<span onclick="logout()">退出</span>' : '';
  const c = $('content'); c.scrollTop = 0;
  if(tab==='index') renderIndex();
  else if(tab==='quote') renderQuote();
  else if(tab==='watch'){ CUR_GID = 0; renderWatch(); }
  else if(tab==='trade') renderTrade();
  else if(tab==='news') renderNews();
}

// 同步底部「首页」Tab 的图标与文字：选中时=刷新(↻ + "刷新")，未选中=房子 + "首页"
function syncTabIcon(){
  const el = document.querySelector('.tabbar .tab[data-tab="index"]');
  if(!el) return;
  const on = el.classList.contains('active');
  const home = el.querySelector('.ico-home');
  const ref  = el.querySelector('.ico-refresh');
  const lb   = el.querySelector('.lb');
  if(home) home.style.display = on ? 'none' : 'block';
  if(ref)  ref.style.display  = on ? 'block' : 'none';
  if(lb)   lb.textContent     = on ? '刷新' : '首页';
}

// 首页已点亮时再点一次 = 刷新当前页
function onTabIndexAgain(){
  if(CUR_TAB === 'index'){
    const c = $('content');
    if(c) c.scrollTop = 0;
    renderIndex();
    toast('已刷新');
  }
}

// ---------- 首页 = 我的（账户 + 会员） ----------
async function renderIndex(){
  const c = $('content');
  c.innerHTML = '<div class="loading">加载中…</div>';
  const [a, m] = await Promise.all([ api('account.php'), api('member.php') ]);
  if(a.code !== 0){ c.innerHTML = '<div class="empty"><span class="e-ico">⚠️</span>加载失败</div>'; return; }
  const d = a.data;
  const vip = (m.code === 0 && m.data) ? m.data : {};
  const isVip = !!vip.isMember;
  const plans = vip.plans || [];

  c.innerHTML = `
    <div class="me-head">
      <div class="me-avatar">${esc((UNAME||'U').slice(0,1).toUpperCase())}</div>
      <div class="me-info">
        <div class="me-name">${esc(UNAME || '用户')}</div>
        <div class="me-vip ${isVip?'on':''}">${isVip ? '👑 ' + esc(vip.levelName||'会员') + ' · 有效期至 ' + esc(vip.expireText||'') : '普通用户 · 未开通会员'}</div>
      </div>
    </div>

    <div class="asset-card">
      <div class="label">总资产（元）</div>
      <div class="total">${num(d.total)}</div>
      <div class="asset-grid">
        <div class="g"><div class="k">可用资金</div><div class="v">${num(d.cash)}</div></div>
        <div class="g"><div class="k">持仓市值</div><div class="v">${num(d.marketValue)}</div></div>
        <div class="g"><div class="k">浮动盈亏</div><div class="v ${cls(d.floatPnl)}">${sign(d.floatPnl)}${num(d.floatPnl)}</div></div>
        <div class="g"><div class="k">累计收益率</div><div class="v ${cls(d.totalPnlPct)}">${sign(d.totalPnlPct)}${num(d.totalPnlPct)}%</div></div>
      </div>
    </div>

    <div class="section">
      <div class="section-title"><b>账户管理</b></div>
      <div class="me-list">
        <div class="me-row" onclick="openSetBalance()">
          <span class="mr-ico">⚙️</span><span class="mr-t">设置资金账户 / 初始余额</span>
          <span class="mr-v">¥${num(d.initial)}</span><span class="mr-a">›</span>
        </div>
        <div class="me-row" onclick="switchTab('trade')">
          <span class="mr-ico">💱</span><span class="mr-t">我的交易 / 持仓</span><span class="mr-a">›</span>
        </div>
        <div class="me-row" onclick="switchTab('watch')">
          <span class="mr-ico">⭐</span><span class="mr-t">我的自选</span><span class="mr-a">›</span>
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-title"><b>会员套餐</b><span>${isVip?'已开通':'选择适合你的方案'}</span></div>
      ${plans.map(p=>`
        <div class="plan ${p.hot?'hot':''}">
          ${p.hot?'<div class="hot-tag">最划算</div>':''}
          <div class="p-ico">${esc(p.icon||'🎖')}</div>
          <div class="p-info">
            <div class="p-name">${esc(p.name)}</div>
            <div class="p-desc">${esc(p.desc)}</div>
          </div>
          <div class="p-price">¥${esc(p.price)}<small>/${esc(p.unit||'')}</small></div>
          <button class="p-btn" onclick="openMemberModal('${esc(p.name)}','${esc(p.price)}','${esc(p.key||'')}')">开通</button>
        </div>`).join('')}
    </div>

    <div class="member-note">模拟数据，仅供学习练习使用，不构成任何投资建议</div>`;
}

// ---------- 自选 ----------
let WATCH_GROUPS = null;   // 分组缓存

async function renderWatch(){
  const c = $('content');
  c.innerHTML = '<div class="loading">加载自选…</div>';
  $('topAct').innerHTML = '<span onclick="openAddWatch()">＋ 添加</span>';

  // 1) 先拉分组
  const g = await api('watchlist.php?action=groups');
  if(g.code !== 0 || !Array.isArray(g.data)){
    c.innerHTML = '<div class="empty"><span class="e-ico">⚠️</span>自选加载失败</div>'; return;
  }
  WATCH_GROUPS = g.data;
  if(!WATCH_GROUPS.length) WATCH_GROUPS = [{id:0,name:'自选股',count:0}];
  if(!CUR_GID || !WATCH_GROUPS.some(x=>x.id===CUR_GID)) CUR_GID = WATCH_GROUPS[0].id;

  const gtabs = WATCH_GROUPS.map(x=>`
    <div class="g ${x.id===CUR_GID?'on':''}"
         onclick="switchGroup(${x.id})"
         ontouchstart="grpTouchStart(event,${x.id})"
         ontouchend="grpTouchEnd(event)"
         oncontextmenu="event.preventDefault();grpMenu(${x.id})">${esc(x.name)}</div>`).join('');

  const head = `
    <div class="idx-bar">${indexBarHtml()}</div>
    <div class="grp-bar" id="grpBar">${gtabs}<div class="g-add" onclick="openGroupAdd()">＋</div></div>`;

  // 2) 再拉当前分组自选
  const j = await api('watchlist.php?action=list&gid='+CUR_GID);
  const list = (j.code===0 && Array.isArray(j.data)) ? j.data : [];
  WATCH_CACHE = list;

  let body;
  if(!list.length){
    body = '<div class="empty"><span class="e-ico">⭐</span>还没有自选股，点右上角「＋ 添加」</div>';
  }else{
    body = list.map(s=>`
      <div class="wrow" id="wr-${esc(s.code)}" data-code="${esc(s.code)}">
        <div class="del" onclick="delWatch('${esc(s.code)}')">删除</div>
        <div class="slid" onclick="rowTap(event,'${esc(s.code)}')">
          <div class="info">
            <div class="nm">${esc(s.name)}</div>
            <div class="cd">${esc(s.code)}</div>
          </div>
          <div class="rt">
            <div class="pct ${s.changePct>0?'pct-up':(s.changePct<0?'pct-down':'')}" data-pct>${sign(s.changePct)}${num(s.changePct)}%</div>
            <div class="prc ${cls(s.changePct)}" data-prc>${num(s.price)}</div>
          </div>
        </div>
      </div>`).join('');
  }

  c.innerHTML = head + body +
    '<div class="member-note">模拟行情，每个交易日随机浮动，仅供练习 · 长按分组可重命名/删除</div>';

  bindRowSlide();
}

// 顶部指数条（用股票池前 3 只模拟三大指数位）
function indexBarHtml(){
  const idxs = [
    {name:'上证指数', v:'3911.87', p:'0.94'},
    {name:'深证成指', v:'12416.05', p:'1.12'},
    {name:'创业板指', v:'2568.33', p:'-0.38'}
  ];
  return idxs.map(x=>`
    <div class="ix">
      <div class="in">${x.name}</div>
      <div class="iv">${x.v}</div>
      <div class="ic ${Number(x.p)>0?'up':(Number(x.p)<0?'down':'flat')}">${Number(x.p)>0?'+':''}${x.p}%</div>
    </div>`).join('');
}

// 切换分组
async function switchGroup(gid){ CUR_GID = gid; renderWatch(); }

// ---------- 分组长按 / 新建 ----------
let __grpTimer = null, __grpMoved = false;
function grpTouchStart(e, gid){
  __grpMoved = false;
  __grpTimer = setTimeout(()=>{ if(!__grpMoved) grpMenu(gid); }, 550);
}
function grpTouchEnd(){ clearTimeout(__grpTimer); }
function grpMenu(gid){
  clearTimeout(__grpTimer);
  const g = (WATCH_GROUPS||[]).find(x=>x.id===gid); if(!g) return;
  $('modalBox').innerHTML = `
    <div class="mask" id="mask" onclick="closeModal(event)">
      <div class="modal" onclick="event.stopPropagation()">
        <h3>${esc(g.name)}</h3>
        <div class="sub">分组操作</div>
        <div class="row-btns">
          <button class="copy" onclick="groupRename(${g.id})">重命名</button>
          <button class="close" onclick="groupDel(${g.id})">删除</button>
        </div>
      </div>
    </div>`;
}
function openGroupAdd(){
  $('modalBox').innerHTML = `
    <div class="mask" id="mask" onclick="closeModal(event)">
      <div class="modal" onclick="event.stopPropagation()">
        <h3>新建分组</h3>
        <div class="sub">最多 12 个字</div>
        <input id="grpName" class="ipt" placeholder="输入分组名称" maxlength="12">
        <div class="row-btns">
          <button class="copy" onclick="doGroupAdd()">确定</button>
          <button class="close" onclick="closeModal()">取消</button>
        </div>
      </div>
    </div>`;
  setTimeout(()=>{ const el=$('grpName'); if(el) el.focus(); }, 80);
}
async function doGroupAdd(){
  const name = ($('grpName').value||'').trim();
  if(!name) return toast('请输入名称');
  const j = await api('watchlist.php?action=group_add&name='+encodeURIComponent(name), null, 'POST');
  toast(j.msg || '已新建');
  if(j.code===0){ closeModal(); CUR_GID = j.data.id; renderWatch(); }
}
function groupRename(gid){
  const g = (WATCH_GROUPS||[]).find(x=>x.id===gid); if(!g) return;
  $('modalBox').innerHTML = `
    <div class="mask" id="mask" onclick="closeModal(event)">
      <div class="modal" onclick="event.stopPropagation()">
        <h3>重命名分组</h3>
        <div class="sub">最多 12 个字</div>
        <input id="grpName" class="ipt" value="${esc(g.name)}" maxlength="12">
        <div class="row-btns">
          <button class="copy" onclick="doGroupRename(${gid})">保存</button>
          <button class="close" onclick="closeModal()">取消</button>
        </div>
      </div>
    </div>`;
}
async function doGroupRename(gid){
  const name = ($('grpName').value||'').trim();
  if(!name) return toast('请输入名称');
  const j = await api('watchlist.php?action=group_rename&gid='+gid+'&name='+encodeURIComponent(name), null, 'POST');
  toast(j.msg || '已保存');
  if(j.code===0){ closeModal(); renderWatch(); }
}
async function groupDel(gid){
  if((WATCH_GROUPS||[]).length <= 1) return toast('至少保留一个分组');
  $('modalBox').innerHTML = `
    <div class="mask" id="mask" onclick="closeModal(event)">
      <div class="modal" onclick="event.stopPropagation()">
        <h3>删除分组</h3>
        <div class="sub">该分组内的自选股也会一并删除</div>
        <div class="row-btns">
          <button class="close" onclick="doGroupDel(${gid})">确认删除</button>
          <button class="copy" onclick="closeModal()">取消</button>
        </div>
      </div>
    </div>`;
}
async function doGroupDel(gid){
  const j = await api('watchlist.php?action=group_del&gid='+gid, null, 'POST');
  toast(j.msg || '已删除');
  if(j.code===0){ closeModal(); if(CUR_GID===gid) CUR_GID=0; renderWatch(); }
}

// ---------- 右滑露出删除 ----------
function bindRowSlide(){
  document.querySelectorAll('.wrow').forEach(row=>{
    const slid = row.querySelector('.slid'); if(!slid) return;
    let x0=0, y0=0, dx=0, moved=false;
    slid.addEventListener('touchstart', e=>{
      const t = e.touches[0]; x0=t.clientX; y0=t.clientY; dx=0; moved=false;
    }, {passive:true});
    slid.addEventListener('touchmove', e=>{
      const t = e.touches[0];
      const mx = t.clientX - x0, my = t.clientY - y0;
      if(Math.abs(my) > Math.abs(mx)) return;   // 纵向滚动交给页面
      moved = true;
      const base = row.classList.contains('open') ? 76 : 0;
      dx = Math.max(0, Math.min(76, base + mx));
      slid.style.transition = 'none';
      slid.style.left = dx + 'px';
    }, {passive:true});
    slid.addEventListener('touchend', ()=>{
      slid.style.transition = '';
      if(dx > 38){ row.classList.add('open'); slid.style.left=''; }
      else { row.classList.remove('open'); slid.style.left=''; }
      if(moved) row.dataset.moved = '1'; else row.dataset.moved = '';
    });
  });
}
// 点击行：若处于滑开状态（或刚从滑动结束）则先收起，否则进入详情
function rowTap(e, code){
  const row = e.currentTarget.closest('.wrow');
  if(row && row.classList.contains('open')){
    row.classList.remove('open'); return;
  }
  if(row && row.dataset.moved){ row.dataset.moved=''; return; }
  openStock(code);
}

// ---------- 添加自选 ----------
async function openAddWatch(){
  $('modalBox').innerHTML = `
    <div class="mask" id="addMask" onclick="if(event.target===this) closeAdd()">
      <div class="modal add-modal">
        <h3>添加自选股</h3>
        <div class="sub">搜索股票代码或名称，点击加入当前分组</div>
        <input id="addKw" class="ipt" placeholder="如 600519 / 茅台" oninput="doSearch()">
        <div class="watch-pick" id="pickBox"><div class="loading">输入关键词搜索…</div></div>
        <button class="close" style="width:100%;height:44px;border-radius:10px;border:1px solid var(--line);background:var(--card2);color:var(--text);font-size:15px" onclick="closeAdd()">关闭</button>
      </div>
    </div>`;
  doSearch();
}
function closeAdd(){ const m=$('addMask'); if(m) m.parentNode.removeChild(m); }
let __searchTimer = null;
function doSearch(){
  clearTimeout(__searchTimer);
  __searchTimer = setTimeout(async ()=>{
    const box = $('pickBox'); if(!box) return;
    const kw = ($('addKw').value||'').trim();
    box.innerHTML = '<div class="loading">搜索中…</div>';
    const j = await api('search.php?kw='+encodeURIComponent(kw));
    const list = (j.code===0 && Array.isArray(j.data)) ? j.data : [];
    if(!list.length){ box.innerHTML = '<div class="empty"><span class="e-ico">🔍</span>没找到相关股票</div>'; return; }
    box.innerHTML = list.map(s=>`
      <div class="pick-row" onclick="addWatch('${esc(s.code)}')">
        <div class="info"><div class="name">${esc(s.name)}</div><div class="code">${esc(s.code)}</div></div>
        <div class="price ${cls(s.changePct)}">${num(s.price)}</div>
      </div>`).join('');
  }, 260);
}
async function addWatch(code){
  const j = await api('watchlist.php?action=add&code='+code+'&gid='+CUR_GID, null, 'POST');
  toast(j.msg || (j.code===0?'已添加':'添加失败'));
  if(j.code===0){ closeAdd(); renderWatch(); }
}
async function delWatch(code){
  const j = await api('watchlist.php?action=del&code='+code+'&gid='+CUR_GID, null, 'POST');
  toast(j.msg || '已删除');
  if(j.code===0) renderWatch();
}

// ---------- 股票详情 ----------
async function openStock(code){
  const c = $('content');
  c.scrollTop = 0;
  c.innerHTML = '<div class="loading">加载…</div>';
  const j = await api('stocks.php');
  const s = (j.code===0 && Array.isArray(j.data)) ? j.data.find(x=>x.code===code) : null;
  if(!s){ c.innerHTML = '<div class="empty"><span class="e-ico">⚠️</span>未找到该股票</div>'; return; }
  CUR_STOCK = s;
  $('topTitle').textContent = s.name;
  $('topBack').textContent = '‹ 返回';
  $('topAct').innerHTML = '';
  const up = s.changePct > 0, down = s.changePct < 0;
  const inWatch = await isWatched(code);
  c.innerHTML = `
    <div class="asset-card">
      <div class="label">${esc(s.name)} · ${esc(s.code)}</div>
      <div class="total ${cls(s.changePct)}">${num(s.price)}</div>
      <div class="asset-grid">
        <div class="g"><div class="k">涨跌额</div><div class="v ${cls(s.changePct)}">${sign(s.change)}${num(s.change)}</div></div>
        <div class="g"><div class="k">涨跌幅</div><div class="v ${cls(s.changePct)}">${sign(s.changePct)}${num(s.changePct)}%</div></div>
        <div class="g"><div class="k">状态</div><div class="v">${up?'上涨':(down?'下跌':'平盘')}</div></div>
      </div>
      <div class="watch-toggle" onclick="toggleWatch('${esc(s.code)}', ${inWatch})">${inWatch?'★ 已自选（点击移除）':'☆ 加自选'}</div>
    </div>
    <div class="kline-card">
      <div class="kline-title"><b>日K线</b><span class="lg"><i class="lg-b">B</i>买入 <i class="lg-s">S</i>卖出</span></div>
      <canvas id="klineCanvas" width="720" height="420"></canvas>
      <div class="kline-note">近60个交易日 · 模拟数据</div>
    </div>
    <div class="seg">
      <div class="s ${CUR_DIR==='buy'?'on':''}" onclick="setDir('buy')">买入</div>
      <div class="s ${CUR_DIR==='sell'?'on':''}" onclick="setDir('sell')">卖出</div>
    </div>
    <div class="trade-box">
      <div class="row"><span class="k">委托价格</span><span>${num(s.price)} 元（模拟市价）</span></div>
      <div class="row"><span class="k">可买/可卖</span><span id="availText">加载中…</span></div>
      <div class="qty-ctrl">
        <button onclick="stepQty(-100)">-100</button>
        <input id="qtyInput" type="number" value="100" min="100" step="100">
        <button onclick="stepQty(100)">+100</button>
      </div>
      <div class="row"><span class="k">预计金额</span><span id="estText">--</span></div>
      <button class="btn ${CUR_DIR==='buy'?'btn-buy':'btn-sell'}" onclick="doTrade()">${CUR_DIR==='buy'?'确认买入':'确认卖出'}</button>
    </div>
    <div class="member-note">模拟交易，不产生任何真实资金往来</div>`;
  $('qtyInput').addEventListener('input', updateEst);
  loadAvail();
  updateEst();
  loadKline(code);
}
// 判断是否已在自选（用缓存，避免重复请求导致闪烁）
async function isWatched(code){
  if(!WATCH_CACHE){
    const j = await api('watchlist.php?action=list');
    WATCH_CACHE = (j.code===0 && Array.isArray(j.data)) ? j.data : [];
  }
  return WATCH_CACHE.some(x=>x.code===code);
}
async function toggleWatch(code, inWatch){
  const j = await api('watchlist.php?action='+(inWatch?'del':'add')+'&code='+code, null, 'POST');
  WATCH_CACHE = null;   // 失效缓存
  if(j.code===0){ toast(inWatch?'已移除自选':'已加入自选'); openStock(code); }
  else toast(j.msg||'操作失败');
}
// ---------- K线绘制 ----------
async function loadKline(code){
  const cv = $('klineCanvas'); if(!cv) return;
  const j = await api('kline.php?code='+code);
  if(j.code!==0 || !j.data){ drawKline(cv, [], []); return; }
  drawKline(cv, j.data.kline||[], j.data.marks||[]);
}
// 高清适配：按设备像素比设置 canvas 真实分辨率，消除模糊
function fitCanvas(cv, cssW, cssH){
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  cv.width = Math.round(cssW * dpr);
  cv.height = Math.round(cssH * dpr);
  cv.style.width = cssW + 'px';
  cv.style.height = cssH + 'px';
  return dpr;
}
function drawKline(cv, ks, marks){
  // 用元素实际显示宽度计算，保证 K 线不被拉伸
  const boxW = cv.parentElement ? Math.max(280, cv.parentElement.clientWidth - 24) : 320;
  const cssW = boxW, cssH = Math.round(boxW * 420 / 720);
  const dpr = fitCanvas(cv, cssW, cssH);
  const ctx = cv.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  const W = cssW, H = cssH;
  ctx.clearRect(0,0,W,H);
  ctx.fillStyle = '#0f1420'; ctx.fillRect(0,0,W,H);

  const padL = 8, padR = 50, padT = 14, padB = 20;
  const cw = W - padL - padR;         // 绘图区宽
  const ch = H - padT - padB;         // 绘图区高
  if(!ks.length){
    ctx.fillStyle='#8492a6'; ctx.font='14px sans-serif'; ctx.textAlign='center';
    ctx.fillText('暂无K线数据', W/2, H/2); return;
  }
  // 价格范围
  let hi = -Infinity, lo = Infinity;
  ks.forEach(k=>{ hi = Math.max(hi, +k.high); lo = Math.min(lo, +k.low); });
  const range = (hi - lo) || 1;
  const yOf = p => padT + (hi - p) / range * ch;   // 价格 -> y
  const step = cw / ks.length;                     // 每根K线宽度
  const bw = Math.max(2, step * 0.62);
  const xOf = i => padL + step * i + step/2;       // 第 i 根中心 x

  // 网格 + 右侧价格刻度
  ctx.strokeStyle = '#1c2436'; ctx.fillStyle = '#5b6b82';
  ctx.font = '10px sans-serif'; ctx.textAlign = 'left'; ctx.lineWidth = 1;
  for(let g=0; g<=4; g++){
    const y = padT + ch * g / 4;
    ctx.beginPath(); ctx.moveTo(padL, y); ctx.lineTo(padL + cw, y); ctx.stroke();
    const p = hi - range * g / 4;
    ctx.fillText(p.toFixed(2), padL + cw + 5, y + 4);
  }

  // 画K线
  ks.forEach((k,i)=>{
    const o = +k.open, c = +k.close, h = +k.high, l = +k.low;
    const rise = c >= o;
    const col = rise ? '#f5455c' : '#22c55e';   // 红涨绿跌
    ctx.strokeStyle = col; ctx.fillStyle = col;
    const x = xOf(i);
    // 影线
    ctx.beginPath(); ctx.moveTo(x, yOf(h)); ctx.lineTo(x, yOf(l)); ctx.stroke();
    // 实体
    const yO = yOf(o), yC = yOf(c);
    const top = Math.min(yO,yC), hh = Math.max(1, Math.abs(yC-yO));
    ctx.fillRect(x - bw/2, top, bw, hh);
  });

  // 画买卖点标记 B / S
  marks.forEach(mk=>{
    const idx = ks.findIndex(k=>k.date === mk.date);
    if(idx < 0) return;
    const x = xOf(idx);
    const price = +mk.price;
    let y = yOf(price);
    const isBuy = mk.type === 'B';
    // 买入标在下方，卖出标在上方
    y = isBuy ? y + 18 : y - 12;
    const r = 8;
    ctx.save();
    ctx.beginPath();
    ctx.arc(x, Math.max(padT + r, Math.min(padT + ch - r, y)), r, 0, Math.PI*2);
    ctx.fillStyle = isBuy ? '#f5455c' : '#22c55e';
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.font = 'bold 11px sans-serif';
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(mk.type, x, Math.max(padT + r, Math.min(padT + ch - r, y)) + 1);
    ctx.restore();
  });
  // 底部最新日期
  ctx.fillStyle = '#5b6b82'; ctx.font = '10px sans-serif'; ctx.textAlign = 'right';
  ctx.fillText(ks[ks.length-1].date, padL + cw, H - 5);
}
function setDir(d){
  CUR_DIR = d;
  // 只更新按钮样式，避免整页重建造成闪烁
  document.querySelectorAll('.seg .s').forEach((el,i)=>{
    el.classList.toggle('on', (i===0&&d==='buy')||(i===1&&d==='sell'));
  });
  const btn = document.querySelector('.trade-box .btn');
  if(btn){
    btn.className = 'btn ' + (d==='buy' ? 'btn-buy' : 'btn-sell');
    btn.textContent = d==='buy' ? '确认买入' : '确认卖出';
  }
  loadAvail();
  updateEst();
}
function stepQty(d){
  const el = $('qtyInput'); let v = parseInt(el.value||'0') + d;
  if(v < 100) v = 100;
  el.value = v; updateEst();
}
function updateEst(){
  if(!CUR_STOCK) return;
  const q = parseInt($('qtyInput').value||'0') || 0;
  $('estText').textContent = '¥' + num(q * CUR_STOCK.price);
}
async function loadAvail(){
  const j = await api('account.php');
  if(j.code === 0){
    const d = j.data;
    if(CUR_DIR === 'buy'){
      $('availText').textContent = '可用资金 ¥' + num(d.cashBalance) + '（约可买 ' + Math.floor(d.cashBalance / CUR_STOCK.price / 100) * 100 + ' 股）';
    }else{
      const p = await api('positions.php');
      let q = 0;
      if(p.code===0) { const h = (p.data||[]).find(x=>x.code===CUR_STOCK.code); q = h ? h.quantity : 0; }
      $('availText').textContent = '可卖 ' + q + ' 股';
    }
  }else $('availText').textContent = '--';
}
async function doTrade(){
  if(!CUR_STOCK) return;
  const q = parseInt($('qtyInput').value||'0') || 0;
  if(q <= 0 || q % 100 !== 0) return toast('数量需为100的整数倍');
  toast(CUR_DIR==='buy' ? '提交买入…' : '提交卖出…');
  const j = await api(CUR_DIR==='buy' ? 'buy.php' : 'sell.php', { code: CUR_STOCK.code, quantity: q });
  if(j.code === 0){
    toast(CUR_DIR==='buy' ? '买入成功' : '卖出成功');
    setTimeout(()=>{ switchTab('trade'); }, 800);
  }else toast(j.msg || '操作失败');
}

// ---------- 交易 Tab（快捷入口 + 资金） ----------
async function renderTrade(){
  const c = $('content');
  c.innerHTML = '<div class="loading">加载资金与持仓…</div>';
  $('topAct').innerHTML = '<span onclick="switchTab(\'quote\')">＋ 交易</span>';
  const [a, p] = await Promise.all([ api('account.php'), api('positions.php') ]);
  if(a.code !== 0){ c.innerHTML = '<div class="empty"><span class="e-ico">⚠️</span>加载失败</div>'; return; }
  const d = a.data, list = (p.code===0 && Array.isArray(p.data)) ? p.data : [];
  let rows = '';
  if(list.length === 0){
    rows = '<div class="empty"><span class="e-ico">📭</span>暂无持仓，去行情页买入试试</div>';
  }else{
    rows = list.map(h=>`
      <div class="trade-box" style="margin:10px 14px" data-code="${esc(h.code)}" data-qty="${h.quantity}" data-cost="${h.avgCost}">
        <div class="row">
          <span><b>${esc(h.name)}</b> <span style="color:var(--sub);font-size:11px">${esc(h.code)}</span></span>
          <span data-h-profit class="${cls(h.pnl)}">${sign(h.pnl)}${num(h.pnl)}</span>
        </div>
        <div class="row"><span class="k">持仓数量</span><span>${h.quantity} 股</span></div>
        <div class="row"><span class="k">成本价 / 现价</span><span data-h-price>${num(h.avgCost)} / ${num(h.price)}</span></div>
        <div class="row"><span class="k">市值</span><span data-h-mv>¥${num(h.marketValue)}</span></div>
        <div class="row"><span class="k">盈亏比例</span><span data-h-pct class="${cls(h.pnl)}">${sign(h.pnlPct)}${num(h.pnlPct)}%</span></div>
        <div class="qty-ctrl" style="margin-bottom:0">
          <button onclick="openStock('${esc(h.code)}')">买入</button>
          <button onclick="quickSell('${esc(h.code)}')">卖出</button>
        </div>
      </div>`).join('');
  }
  c.innerHTML = `
    <div class="asset-card">
      <div class="label">总资产（元）</div>
      <div class="total">${num(d.total)}</div>
      <div class="asset-grid">
        <div class="g"><div class="k">可用资金</div><div class="v">${num(d.cash)}</div></div>
        <div class="g"><div class="k">持仓市值</div><div class="v">${num(d.marketValue)}</div></div>
        <div class="g"><div class="k">浮动盈亏</div><div class="v ${cls(d.floatPnl)}">${sign(d.floatPnl)}${num(d.floatPnl)}</div></div>
        <div class="g"><div class="k">累计收益率</div><div class="v ${cls(d.totalPnlPct)}">${sign(d.totalPnlPct)}${num(d.totalPnlPct)}%</div></div>
      </div>
    </div>
    <div class="section-title" style="margin:14px 14px 8px"><b>我的持仓</b><span>${list.length} 只</span></div>
    ${rows}
    <div class="member-note">交易为模拟撮合，价格以当前模拟行情成交</div>`;
}
function quickSell(code){
  CUR_DIR = 'sell';
  openStock(code);
}

// ---------- 行情 ----------
let QUOTE_CAT = 'A股';   // 当前行情分类
async function renderQuote(){
  const c = $('content');
  c.innerHTML = '<div class="loading">加载行情…</div>';
  $('topAct').innerHTML = '<span onclick="switchTab(\'watch\')">自选</span>';
  const j = await api('stocks.php');
  const list = (j.code===0 && Array.isArray(j.data)) ? j.data : [];
  const open = __isTradeTime();
  const cats = ['大盘','A股','港股','美股','基金'];
  const catBar = cats.map(x=>`<div class="q-cat ${x===QUOTE_CAT?'on':''}" onclick="setQuoteCat('${x}')">${x}</div>`).join('');

  // 指数：优先真实行情缓存里的指数代码；否则用股票池前 3 只占位
  const idxHtml = realIndexBarHtml(list, open);

  // 成交额：累加股票池成交额（无则占位）
  let amount = 0;
  list.forEach(s=>{ amount += Number(s.amount||0); });
  const amountText = amount > 0 ? (amount/1e8).toFixed(0) + ' 亿' : '--';

  // 涨跌家数
  let up=0, down=0, flat=0;
  list.forEach(s=>{ const p=Number(s.changePct||0); if(p>0) up++; else if(p<0) down++; else flat++; });

  const rowHtml = list.map(s=>`
    <div class="stock-row" data-code="${esc(s.code)}" onclick="openStock('${esc(s.code)}')">
      <div class="info"><div class="name">${esc(s.name)}</div><div class="code">${esc(s.code)}</div></div>
      <div class="right"><div class="price ${cls(s.changePct)}" data-prc>${num(s.price)}</div></div>
      <div style="width:80px;text-align:right"><span class="chg-tag ${s.changePct>0?'up':(s.changePct<0?'down':'flat')}" data-pct>${sign(s.changePct)}${num(s.changePct)}%</span></div>
    </div>`).join('');

  c.innerHTML = `
    <div class="q-catbar">${catBar}</div>
    <div class="q-status"><span class="dot ${open?'live':'off'}"></span>${open?'开盘中 · 实时刷新':'已收盘 · 显示上一交易日'}</div>
    <div class="idx-card" id="idxCard">${idxHtml}</div>
    <div class="q-metrics">
      <div class="qm"><div class="k">两市成交额</div><div class="v">${amountText}</div></div>
      <div class="qm"><div class="k">上涨家数</div><div class="v red">${up}</div></div>
      <div class="qm"><div class="k">下跌家数</div><div class="v green">${down}</div></div>
    </div>
    <div class="section-title" style="margin:14px 14px 8px"><b>${esc(QUOTE_CAT)}行情</b><span>点击查看详情</span></div>
    ${rowHtml || '<div class="empty"><span class="e-ico">📊</span>暂无行情数据</div>'}
    <div class="member-note">行情数据来自公开接口，仅供学习练习使用</div>`;
}
function setQuoteCat(x){ QUOTE_CAT = x; renderQuote(); }

// 真实指数条：用指数代码从上证/深证/创业板取（后端 stocks.php 若含指数则直接用）
function realIndexBarHtml(list, open){
  const pick = (want) => list.find(s=> String(s.name||'').indexOf(want) >= 0 || String(s.code||'').indexOf(want) >= 0);
  const defs = [
    {key:'上证', code:'000001', name:'上证指数'},
    {key:'深证', code:'399001', name:'深证成指'},
    {key:'创业', code:'399006', name:'创业板指'}
  ];
  return defs.map(d0=>{
    const s = pick(d0.key) || null;
    const v = s ? num(s.price) : '--';
    const p = s ? Number(s.changePct||0) : 0;
    return `<div class="idx-item" data-code="${esc(s?s.code:d0.code)}">
      <div class="n">${esc(s?s.name:d0.name)}</div>
      <div class="v ${cls(p)}">${v}</div>
      <div class="c ${cls(p)}">${s?sign(p)+num(p)+'%':'--'}</div>
    </div>`;
  }).join('');
}

// ---------- 资讯 ----------
let NEWS_CAT = '要闻';
async function renderNews(){
  const c = $('content');
  $('topAct').innerHTML = '';
  const cats = ['要闻','快讯','深度研究'];
  const catBar = cats.map(x=>`<div class="q-cat ${x===NEWS_CAT?'on':''}" onclick="setNewsCat('${x}')">${x}</div>`).join('');
  const topics = ['头条','PCB概念','算力租赁','机器人','半导体','光伏','医药'];
  const topicBar = topics.map((t,i)=>`<div class="n-topic ${i===0?'on':''}">${t}</div>`).join('');
  // 资讯内容：本地模拟（后端无资讯接口时使用）
  const feeds = [
    ['两市股指震荡上行，AI 算力板块午后拉升', '模拟财经 · 5分钟前'],
    ['北向资金今日净买入超 50 亿元', '模拟财经 · 18分钟前'],
    ['多家券商发布研报看好半导体设备国产化', '模拟财经 · 42分钟前'],
    ['新能源车销量数据超预期，产业链公司业绩修复', '模拟财经 · 1小时前'],
    ['央行开展逆回购操作，维护市场流动性合理充裕', '模拟财经 · 2小时前'],
    ['PCB 行业景气度回升，龙头厂商获机构调研', '模拟财经 · 3小时前']
  ];
  c.innerHTML = `
    <div class="q-catbar">${catBar}</div>
    <div class="n-topics">${topicBar}</div>
    <div class="n-feed">
      ${feeds.map(f=>`
        <div class="n-item">
          <div class="n-t">${esc(f[0])}</div>
          <div class="n-s">${esc(f[1])}</div>
        </div>`).join('')}
    </div>
    <div class="member-note">资讯为模拟内容，仅供学习练习使用</div>`;
}
function setNewsCat(x){ NEWS_CAT = x; renderNews(); }

// ---------- 设置初始余额 ----------
function openSetBalance(){
  $('modalBox').innerHTML = `
    <div class="mask" id="mask" onclick="closeModal(event)">
      <div class="modal" onclick="event.stopPropagation()">
        <h3>设置初始余额</h3>
        <div class="sub">设置模拟资金账户的初始本金（元）</div>
        <input id="initInput" class="modal-input" type="number" placeholder="例如 100000" />
        <div class="row-btns">
          <button class="copy" onclick="saveSetBalance()">保存</button>
          <button class="close" onclick="closeModal()">取消</button>
        </div>
      </div>
    </div>`;
}
async function saveSetBalance(){
  const v = Number($('initInput').value||0);
  if(!(v > 0)) return toast('请输入有效的金额');
  const j = await api('account.php', { action:'set_balance', initial: v });
  if(j.code === 0){ closeModal(); toast('已更新初始余额'); renderIndex(); }
  else toast(j.msg || '保存失败（后端暂未支持）');
}

// ---------- 会员开通弹窗（引导联系客服） ----------
function openMemberModal(name, price, plan){
  const qq = '982088304';
  $('modalBox').innerHTML = `
    <div class="mask" id="mask" onclick="closeModal(event)">
      <div class="modal" onclick="event.stopPropagation()">
        <h3>开通「${esc(name)}」</h3>
        <div class="sub">价格 ¥${esc(price)} · 模拟应用可立即生效</div>
        <div class="qq-box">
          <div class="lbl">客服 QQ（如需人工办理）</div>
          <div class="qq" id="qqText">${qq}</div>
        </div>
        <div class="row-btns">
          <button class="copy" onclick="doBuyMember('${esc(plan||'')}')">模拟开通</button>
          <button class="close" onclick="copyQQ('${qq}')">复制QQ</button>
        </div>
        <button class="modal-btn ghost" style="margin-top:10px" onclick="closeModal()">关闭</button>
      </div>
    </div>`;
}
async function doBuyMember(plan){
  const j = await api('member.php?action=buy&plan=' + encodeURIComponent(plan), { action:'buy', plan: plan });
  if(j.code === 0){ closeModal(); toast('已开通「' + (j.data.levelName||'会员') + '」'); renderIndex(); }
  else toast(j.msg || '开通失败');
}
function closeModal(e){
  if(e && e.target && e.target.id !== 'mask') return;
  $('modalBox').innerHTML = '';
}
function copyQQ(qq){
  const done = ()=>toast('已复制客服 QQ：' + qq);
  if(navigator.clipboard && navigator.clipboard.writeText){
    navigator.clipboard.writeText(qq).then(done).catch(()=>fallbackCopy(qq, done));
  }else fallbackCopy(qq, done);
}
function fallbackCopy(text, cb){
  const ta = document.createElement('textarea');
  ta.value = text; ta.style.position='fixed'; ta.style.opacity='0';
  document.body.appendChild(ta); ta.select();
  try{ document.execCommand('copy'); cb(); }catch(e){ toast('复制失败，请手动记录：' + text); }
  document.body.removeChild(ta);
}

// ---------- 返回 ----------
function goBack(){
  if(CUR_STOCK){ CUR_STOCK = null; switchTab(CUR_TAB); }
}

// ---------- 初始化 ----------
(function init(){
  if(TOKEN){
    api('account.php').then(j=>{
      if(j.code === 0) enterApp();
      else logout(true);
    });
  }
})();
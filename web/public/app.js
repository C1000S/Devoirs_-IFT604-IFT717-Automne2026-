import {api, uid, roles, states, priorities, readSession, saveSession, expired, clearSessionCache, stream} from './data.js';
import {h, link, panel, card, paragraph, field, area, select, details, form, date, money, rows, data} from './ui.js';
import {management} from './management.js';

const app = document.querySelector('#app');
const header = document.querySelector('#header');
const connection = document.querySelector('#connection');
let session = readSession();
let currentView = 0;
let liveController;
let live = false;
let staleAt;
let expiration;
let dirty = false;
let refreshTimer;
let deferredInstall;
const pairs = Object.entries;
const catalog = items => items.map(item => [uid(item), item.name]);
const placeholder = (label, options) => [['', label], ...options];
const isManager = () => ['admin','coordinator'].includes(session?.user.role);
const stateLabel = value => states[value] || value || '—';
const name = value => value?.name || (value ? uid(value) : '—');
const get = async (path, selected = session) => {
  const response = await api(path, {session:selected, cache:true});
  if (session === selected && response.offline) {
    staleAt = Math.min(staleAt || Infinity, response.savedAt);
    updateConnection();
  }
  return response.value;
};

async function logout(message) {
  const previous = session;
  session = null;
  saveSession(null);
  liveController?.abort();
  clearTimeout(expiration);
  live = false;
  location.hash = '';
  render(message);
  await clearSessionCache(previous).catch(() => {});
}
function activate(next) {
  session = next;
  saveSession(next);
  liveController?.abort();
  liveController = new AbortController();
  clearTimeout(expiration);
  expiration = setInterval(() => { if (expired(next)) logout('Session expirée.'); }, 1000);
  reconnect(next, liveController.signal);
}
function updateConnection() {
  connection.textContent = !session ? '' : staleAt
    ? 'Hors ligne · données consultées le ' + date(staleAt)
    : !navigator.onLine ? 'Hors ligne · consultation des données enregistrées'
    : live ? 'Temps réel connecté' : 'Connexion au temps réel en cours…';
  connection.className = staleAt || !navigator.onLine ? 'offline' : '';
}
function queueRefresh() {
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => {
    if (dirty || app.querySelector('form button:disabled')) return;
    if (session) render();
  }, 250);
}
async function reconnect(selected, signal) {
  let delay = 1000;
  while (!signal.aborted) {
    try {
      await stream(selected, signal, event => {
        if (signal.aborted) return;
        if (event === 'connected') {
          live = true;
          delay = 1000;
          updateConnection();
        }
        if (['connected','repair-request','notification'].includes(event)) queueRefresh();
      });
    } catch (error) {
      if (!signal.aborted && error.status === 401) { await logout('Session expirée.'); return; }
    }
    if (signal.aborted) return;
    live = false;
    updateConnection();
    await new Promise(resolve => setTimeout(resolve, delay));
    delay = Math.min(delay * 2, 15000);
  }
}
async function mutate(path, method, body) {
  if (!navigator.onLine) throw new Error('Action indisponible hors ligne. Les modifications ne sont pas mises en attente.');
  const selected = session;
  try {
    const response = await api(path, {session:selected, method, body});
    if (session === selected) dirty = false;
    return response.value;
  } catch (error) {
    if (session === selected && error.status === 401) logout('Session expirée.');
    throw error;
  }
}
const action = (label, fields, path, method='PATCH', map=data) =>
  form(label, fields, async fd => {
    await mutate(path, method, await map(fd));
    queueRefresh();
  });

function top(route) {
  const brand = link('RépareCampus', '', 'brand');
  const links = [['','Demandes'],['dashboard','Tableau de bord'],['notifications','Notifications'],['categories','Catégories'],['slots','Créneaux'],['parts','Pièces'],['profile','Profil']];
  if (isManager()) links.splice(6, 0, ['users','Comptes']);
  const nav = session && h('nav', {'aria-label':'Navigation principale'}, links.map(([path, title]) => {
    const item = link(title, path);
    if (route === path) item.setAttribute('aria-current','page');
    return item;
  }));
  header.replaceChildren(h('div', {class:'top'}, brand, session && h('div',{class:'account'},
    h('span',{}, session.user.name + ' · ' + roles[session.user.role]),
    deferredInstall && h('button',{class:'secondary',onclick:async () => {await deferredInstall.prompt(); deferredInstall = null; top(route);}},'Installer'),
    h('button',{class:'secondary',onclick:() => logout()},'Déconnexion'))), ...[nav].filter(Boolean));
}
function auth(register, message) {
  return h('div',{class:'auth'}, h('section',{class:'welcome'}, h('p',{class:'kicker'},'Clinique de réparation collaborative'), h('h1',{},'Donnez une seconde vie à vos appareils.'), paragraph('Soumettez une demande et suivez sa réparation sur le campus.')),
    panel(register ? 'Créer un compte' : 'Connexion',
      message && h('p',{role:'status'},message),
      form(register ? 'Créer mon compte' : 'Se connecter', [
        register && field('name','Nom complet',{required:true,autocomplete:'name'}),
        field('email','Courriel',{type:'email',required:true,autocomplete:'username'}),
        field('password','Mot de passe',{type:'password',required:true,minlength:register ? 8 : undefined,autocomplete:register ? 'new-password':'current-password'})
      ], async fd => {
        const {value} = await api('/auth/' + (register ? 'register' : 'login'), {method:'POST',body:{...data(fd),password:fd.get('password')}});
        activate({...value,cache:crypto.randomUUID()});
        location.hash = '#';
        render();
      }),
      link(register ? 'Déjà un compte ? Se connecter' : 'Créer un compte demandeur', register ? '' : 'register')));
}
function requestCard(request, categories) {
  return card(h('div',{class:'line'},
    h('div',{},h('h2',{},link([request.device.type,request.device.brand,request.device.model].filter(Boolean).join(' '),'request/' + uid(request))),
      paragraph(categories.find(category => uid(category) === request.category)?.name || request.category)),
    h('span',{class:'badge'},stateLabel(request.status))),
    paragraph(request.problemDescription),
    h('div',{class:'meta'},date(request.createdAt),' · Priorité ',priorities[request.priority] || request.priority,' · ',request.assignedTo ? name(request.assignedTo) : 'Sans affectation'));
}
async function listView() {
  const [requests, categories] = await Promise.all([get('/repair-requests'), get('/categories')]);
  const out = h('div',{class:'cards'});
  const count = h('p',{role:'status'});
  const repairers = new Map(requests.filter(r => r.assignedTo).map(r => [uid(r.assignedTo),name(r.assignedTo)]));
  const filter = h('form',{class:'filters',oninput:apply,onsubmit:event=>event.preventDefault()},
    field('q','Recherche',{type:'search',placeholder:'Objet, marque, problème…'}),
    select('status','État',placeholder('Tous les états',pairs(states))),
    select('category','Catégorie',placeholder('Toutes les catégories',catalog(categories))),
    select('priority','Priorité',placeholder('Toutes les priorités',pairs(priorities))),
    select('repairer','Réparation',placeholder('Toutes les affectations',[['none','Sans affectation'],...repairers])),
    select('estimate','Estimation',[['','Toutes'],['yes','Avec estimation'],['no','Sans estimation']]),
    field('from','Créée depuis',{type:'date'}),field('to','Créée jusqu’au',{type:'date'}),
    select('sort','Trier par',[['','Plus récentes'],['old','Plus anciennes'],['priority','Priorité']]));
  const route = location.hash || '#';
  for (const input of filter.elements) input.value = filterValues.get(route)?.[input.name] || '';
  function apply() {
    const f = data(new FormData(filter));
    filterValues.set(route,f);
    const filtered = requests.filter(r => {
      const words = [r.device.type,r.device.brand,r.device.model,r.problemDescription,name(r.assignedTo)].join(' ').toLocaleLowerCase();
      const day = r.createdAt.slice(0,10);
      return words.includes(f.q.toLocaleLowerCase()) && (!f.status || r.status === f.status)
        && (!f.category || r.category === f.category) && (!f.priority || r.priority === f.priority)
        && (!f.repairer || (uid(r.assignedTo) || 'none') === f.repairer)
        && (!f.estimate || (typeof r.estimate?.cost === 'number') === (f.estimate === 'yes'))
        && (!f.from || day >= f.from) && (!f.to || day <= f.to);
    });
    if (f.sort === 'old') filtered.sort((a,b)=>a.createdAt.localeCompare(b.createdAt));
    if (f.sort === 'priority') filtered.sort((a,b)=>Object.keys(priorities).indexOf(b.priority)-Object.keys(priorities).indexOf(a.priority));
    count.textContent = filtered.length + ' demande(s)';
    out.replaceChildren(...rows(filtered,r=>requestCard(r,categories)));
  }
  apply();
  return [h('div',{class:'line'},h('div',{},h('p',{class:'kicker'},'Atelier du campus'),h('h1',{},'Demandes de réparation')),
    ['requester','admin'].includes(session.user.role) && link('Nouvelle demande','new','button')),panel('Rechercher et filtrer',filter), count, out];
}
const filterValues = new Map();
const terminalStates = ['closed','cancelled','not_repairable'];
function requestFields(categories, r = {}) {
  return [
    select('category','Catégorie',placeholder('Choisir une catégorie', catalog(categories)),r.category || '',true),
    field('type','Type d’objet',{required:true,value:r.device?.type}),
    field('brand','Marque',{required:true,value:r.device?.brand}),
    field('model','Modèle',{value:r.device?.model}),
    area('problemDescription','Description du problème',r.problemDescription,true),
    field('contactMethod','Moyen de contact',{required:true,value:r.contactMethod}),
    select('priority','Priorité',pairs(priorities),r.priority || 'normal'),
    area('photoUrls','Liens directs des photos (un par ligne)',(r.photos || []).join('\n'))
  ];
}
function requestBody(fd, existing=false) {
  const {type,brand,model,photoUrls,...values} = data(fd);
  const photos = photoUrls.split(/\r?\n/).map(url=>url.trim()).filter(Boolean);
  if ((!existing && !photos.length) || photos.some(url => !safePhoto(url))) throw new Error('Ajoutez une adresse de photo HTTP ou HTTPS valide.');
  return {...values,device:{type,brand,model},photos};
}
function safePhoto(value) {
  try { return ['http:','https:'].includes(new URL(value).protocol); }
  catch { return false; }
}
async function newRequest() {
  const categories = await get('/categories');
  return panel('Nouvelle demande',form('Envoyer la demande',requestFields(categories),async fd=>{
    const saved = await mutate('/repair-requests','POST',requestBody(fd));
    location.hash = 'request/' + uid(saved);
  }));
}
function partFields(parts, usages=[]) {
  const list = h('div',{class:'fields'});
  const options = catalog(parts);
  function add(item={}) {
    const row = h('div',{class:'line fields'},
      select('part','Pièce',placeholder('Choisir une pièce',options),uid(item.part) || '',true),
      field('quantity','Quantité',{type:'number',min:1,required:true,value:item.quantity || 1}),
      h('button',{type:'button',class:'secondary',onclick:()=>row.remove()},'Retirer'));
    list.append(row);
  }
  usages.filter(item=>options.some(([id])=>id === uid(item.part))).forEach(add);
  return h('div',{},list,h('button',{type:'button',class:'secondary',onclick:()=>add()},'Ajouter une pièce'));
}
function partPayload(fd) {
  const parts = fd.getAll('part');
  if (parts.length !== new Set(parts).size) throw new Error('Regroupez les quantités de la même pièce sur une ligne.');
  return parts.map((part,i)=>({part,quantity:Number(fd.getAll('quantity')[i])}));
}
function usage(items=[]) {
  return items.length ? h('ul',{},items.map(item=>h('li',{},name(item.part) + ' × ' + item.quantity))) : null;
}
async function detailView(id) {
  const path = '/repair-requests/' + encodeURIComponent(id);
  const r = await get(path);
  const manager = isManager();
  const repairer = session.user.role === 'repairer' && uid(r.assignedTo) === uid(session.user);
  const owner = uid(r.requester) === uid(session.user);
  const requester = session.user.role === 'requester' && owner;
  const [categories,parts,repairers,slots] = await Promise.all([
    get('/categories'), repairer ? get('/parts') : [], manager ? get('/users?role=repairer') : [], get('/slots')
  ]);
  const eligible = repairers.filter(u=>u.skills?.includes(r.category));
  const terminal = terminalStates.includes(r.status);
  const actions = [
    (requester || (session.user.role === 'admin' && owner)) && !terminal && details('Compléter ou corriger la demande',action('Enregistrer les informations',requestFields(categories,r),path,'PATCH',fd=>requestBody(fd,true))),
    manager && !terminal && details('Affecter ou réaffecter', eligible.length ?
      action('Confirmer l’affectation',[select('assignedTo','Personne qualifiée',placeholder('Choisir',catalog(eligible)),uid(r.assignedTo) || '',true)],path+'/assign')
      : paragraph('Aucune personne réparatrice ne possède cette compétence.')),
    manager && !terminal && details('Planifier sur un créneau',action('Planifier',[select('slotId','Créneau',placeholder('Choisir',slots.map(s=>[uid(s),s.date.slice(0,10)+' · '+s.startTime+'–'+s.endTime])),uid(r.scheduledSlot) || '',true)],path+'/schedule')),
    repairer && !terminal && details('Diagnostic',action('Enregistrer le diagnostic',[area('text','Diagnostic',r.diagnosis?.text,true),partFields(parts,r.diagnosis?.partsNeeded)],path+'/diagnosis','PATCH',fd=>({text:fd.get('text').trim(),partsNeeded:partPayload(fd)}))),
    repairer && !terminal && details('Estimation',action('Envoyer l’estimation',[field('cost','Coût estimé ($ CA)',{type:'number',min:0,step:'0.01',required:true,value:r.estimate?.cost}),area('description','Description',r.estimate?.description)],path+'/estimate','PATCH',fd=>({...data(fd),cost:Number(fd.get('cost'))}))),
    requester && r.estimate?.status === 'pending' && r.status === 'waiting' && details('Répondre à l’estimation',action('Confirmer la réponse',[select('accept','Décision',[['','Choisir'],['yes','Accepter'],['no','Refuser']],'',true)],path+'/estimate-response','PATCH',fd=>({accept:fd.get('accept')==='yes'}))),
    repairer && !terminal && details('Intervention',action('Consigner l’intervention',[area('description','Travail réalisé','',true),partFields(parts)],path+'/interventions','POST',fd=>({description:fd.get('description').trim(),partsUsed:partPayload(fd)}))),
    (manager || repairer) && r.allowedTransitions?.length > 0 && details('Changer l’état',action('Appliquer la transition',[
      select('status','Nouvel état',placeholder('Choisir',r.allowedTransitions.map(s=>[s,stateLabel(s)])),'',true),
      field('reason','Motif (fermeture, annulation ou objet non réparable)')
    ],path+'/status','PATCH',fd=>{
      const values=data(fd);
      if (terminalStates.includes(values.status) && !values.reason) throw new Error('Indiquez un motif pour terminer la demande.');
      return values;
    }))
  ].filter(Boolean);
  return [
    link('← Toutes les demandes',''),h('div',{class:'line'},h('h1',{},[r.device.type,r.device.brand,r.device.model].filter(Boolean).join(' ')),h('span',{class:'badge'},stateLabel(r.status))),
    panel('Informations',paragraph(r.problemDescription),h('dl',{},
      ['Catégorie',categories.find(c=>uid(c)===r.category)?.name || r.category,'Contact',r.contactMethod || '—','Demande',name(r.requester),'Affectation',name(r.assignedTo),'Priorité',priorities[r.priority] || r.priority,'Création',date(r.createdAt),'Motif de clôture',r.closedReason || '—'].map((item,i)=>h(i%2 ? 'dd':'dt',{},item))),
      slots.find(s=>uid(s)===uid(r.scheduledSlot)) && paragraph('Créneau confirmé · '+slots.find(s=>uid(s)===uid(r.scheduledSlot)).date.slice(0,10)+' '+slots.find(s=>uid(s)===uid(r.scheduledSlot)).startTime),
      h('div',{class:'photos'},(r.photos || []).filter(safePhoto).map(src=>h('img',{src,alt:'Photo de l’objet à réparer',loading:'lazy',referrerpolicy:'no-referrer'})))),
    r.diagnosis?.text && panel('Diagnostic',paragraph(r.diagnosis.text),usage(r.diagnosis.partsNeeded)),
    typeof r.estimate?.cost === 'number' && panel('Estimation',paragraph(money(r.estimate.cost)+' · '+({pending:'En attente',accepted:'Acceptée',refused:'Refusée'}[r.estimate.status] || '')),paragraph(r.estimate.description)),
    r.interventions?.length > 0 && panel('Interventions',r.interventions.map(i=>card(paragraph(i.description),paragraph(name(i.performedBy)+' · '+date(i.performedAt)),usage(i.partsUsed)))),
    actions.length > 0 && panel('Actions',actions),
    panel('Historique',h('ol',{class:'timeline'},rows(r.events || [],e=>h('li',{},h('strong',{},e.message),h('span',{},date(e.createdAt)+' · '+name(e.actor))))))
  ];
}
async function dashboard() {
  const [requests,categories,parts] = await Promise.all([get('/repair-requests'),get('/categories'),get('/parts')]);
  function chart(title, key, label) {
    const counts = new Map();
    for (const r of requests) {
      const id = key(r);
      if (!counts.has(id)) counts.set(id,{label:label(r),count:0});
      counts.get(id).count++;
    }
    const sorted = [...counts.values()].sort((a,b)=>b.count-a.count);
    return panel(title,rows(sorted,({label,count})=>h('div',{class:'bar-row'},h('span',{},label),h('meter',{min:0,max:sorted[0].count,value:count,'aria-label':label},String(count)),h('strong',{},String(count)))));
  }
  let quoted = 0;
  let catalogCost = 0;
  let unpriced = 0;
  for (const r of requests) {
    if (typeof r.estimate?.cost === 'number') quoted += r.estimate.cost;
    for (const intervention of r.interventions || []) {
      for (const usage of intervention.partsUsed || []) {
        const part = parts.find(p=>uid(p)===uid(usage.part));
        if (typeof part?.unitCost === 'number') catalogCost += part.unitCost * usage.quantity;
        else unpriced++;
      }
    }
  }
  return [h('h1',{},'Tableau de bord'),paragraph(requests.length + ' demande(s) accessible(s) avec votre profil.'),
    h('div',{class:'charts'},chart('Par état',r=>r.status,r=>stateLabel(r.status)),
      chart('Par catégorie',r=>r.category,r=>categories.find(c=>uid(c)===r.category)?.name || r.category),
      chart('Par personne réparatrice',r=>uid(r.assignedTo),r=>r.assignedTo ? name(r.assignedTo) : 'Sans affectation')),
    panel('Montants',paragraph('Estimations proposées, toutes décisions confondues · '+money(quoted)),
      paragraph('Pièces utilisées au prix actuel du catalogue · '+money(catalogCost)+(unpriced ? ' ('+unpriced+' ligne(s) sans prix disponible)' : '')),
      paragraph('Dépenses constatées · données indisponibles dans l’API.'))];
}
async function notifications() {
  const users = session.user.role === 'admin' ? await get('/users') : [];
  const search = new URLSearchParams(location.hash.split('?')[1] || '');
  const userId = search.get('userId');
  const notifications = await get('/notifications' + (userId && users.length ? '?userId='+encodeURIComponent(userId) : ''));
  const filter = users.length && form('Afficher',[select('userId','Compte',placeholder('Mes notifications',catalog(users)),userId || '')],fd=>{
    location.hash = 'notifications' + (fd.get('userId') ? '?userId='+encodeURIComponent(fd.get('userId')) : '');
  });
  return [h('h1',{},'Notifications'),filter,h('div',{class:'cards'},rows(notifications,n=>card(
    h('div',{class:'line'},h('h2',{},n.title),h('span',{class:'badge'},n.read ? 'Lue' : 'Nouvelle')),
    paragraph(n.message),paragraph(date(n.createdAt)),n.request && link('Voir la demande','request/'+uid(n.request)),
    !n.read && action('Marquer comme lue',[], '/notifications/'+uid(n)+'/read')
  )))];
}
async function profile() {
  const user = await get('/auth/me');
  return panel('Mon profil',paragraph(user.email+' · '+roles[user.role]),paragraph('Compétences · '+(user.skills?.join(', ') || '—')),
    form('Modifier le nom',[field('name','Nom affiché',{value:user.name,required:true,maxlength:120})],async fd=>{
      const updated = await mutate('/auth/me','PATCH',data(fd));
      session = {...session,user:updated};
      activate(session);
      queueRefresh();
    }));
}
async function render(message) {
  const ticket = ++currentView;
  const route = (location.hash.slice(1).split('?')[0]);
  const selected = session;
  staleAt = null;
  dirty = false;
  top(route);
  updateConnection();
  if (!selected) { app.replaceChildren(auth(route==='register',message)); return; }
  try {
    let result;
    if (route.startsWith('request/')) result = await detailView(route.slice(8));
    else if (route === 'new' && ['requester','admin'].includes(selected.user.role)) result = await newRequest();
    else if (route === 'dashboard') result = await dashboard();
    else if (route === 'notifications') result = await notifications();
    else if (route === 'profile') result = await profile();
    else if (['categories','slots','parts','users'].includes(route)) result = await management(route,{get,action,mutate,queueRefresh,role:selected.user.role});
    else result = await listView();
    if (currentView === ticket) app.replaceChildren(...[result].flat());
  } catch (error) {
    if (currentView !== ticket) return;
    if (error.status === 401) { logout('Session expirée.'); return; }
    app.replaceChildren(panel('Page indisponible',h('p',{role:'alert'},error.message),h('button',{onclick:()=>render()},'Réessayer')));
  }
}
window.addEventListener('hashchange',()=>{app.textContent='Chargement…';render();});
window.addEventListener('online',queueRefresh);
window.addEventListener('offline',()=>{updateConnection();queueRefresh();});
window.addEventListener('beforeinstallprompt',event=>{event.preventDefault();deferredInstall=event;top(location.hash.slice(1));});
app.addEventListener('input',event=>{if (event.target.closest('form:not(.filters)')) dirty=true;});
if (session && !expired(session)) activate(session);
else if (session) logout();
render();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(console.error);

import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {setTimeout as wait} from 'node:timers/promises';

const chrome = process.env.CHROME_BIN || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const appOrigin = process.env.WEB_ORIGIN || 'http://127.0.0.1:5173';
const profile = await mkdtemp(join(tmpdir(),'reparecampus-chrome-'));
const port = 19337;
const browser = spawn(chrome,['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--disable-crashpad-for-testing',
  '--remote-debugging-port='+port,'--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:'ignore'});
let socket;
let launchError;
browser.on('error',error=>{ launchError=error; });
async function until(fn, description) {
  const deadline = Date.now()+12000;
  let last;
  while (Date.now()<deadline) {
    try { const result = await fn(); if (result) return result; }
    catch (error) {last=error;}
    await wait(130);
  }
  throw new Error('Délai dépassé : '+description+(last ? ' ('+last.message+')' : ''));
}
try {
  const target = await until(async()=>{
    if (launchError) throw launchError;
    if (browser.exitCode !== null) throw new Error('Chrome a quitté avec le code '+browser.exitCode);
    const response = await fetch('http://127.0.0.1:'+port+'/json/list',{signal:AbortSignal.timeout(400)});
    return (await response.json()).find(tab=>tab.type==='page');
  },'démarrage Chrome');
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{
    socket.addEventListener('open',resolve,{once:true});
    socket.addEventListener('error',reject,{once:true});
  });
  let serial=0;
  const pending = new Map();
  const errors=[];
  socket.addEventListener('message',({data})=>{
    const payload=JSON.parse(data);
    if (payload.method==='Runtime.exceptionThrown') errors.push(payload.params.exceptionDetails.text);
    const handle=pending.get(payload.id);
    if (handle) {pending.delete(payload.id); handle(payload);}
  });
  function send(method,params={}) {
    const id=++serial;
    return new Promise((resolve,reject)=>{
      pending.set(id,payload=>payload.error ? reject(new Error(payload.error.message)) : resolve(payload.result));
      socket.send(JSON.stringify({id,method,params}));
    });
  }
  async function page(fn,...args) {
    const expression='('+fn.toString()+')('+args.map(arg=>JSON.stringify(arg)).join(',')+')';
    const out=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true,userGesture:true});
    if (out.exceptionDetails) throw new Error(out.exceptionDetails.exception?.description || out.exceptionDetails.text);
    return out.result.value;
  }
  const text = (selector,needle)=>page((sel,value)=>document.querySelector(sel)?.textContent.includes(value),selector,needle);
  const route = url=>page(value=>{location.hash=value;},url);
  const login = async (email,role)=>{
    await until(()=>text('#app h2','Connexion'),'formulaire de connexion');
    await page(email=>{
      const form=document.querySelector('#app form');
      form.elements.email.value=email+'@example.test';
      form.elements.password.value='Demo1234!';
      form.requestSubmit();
    },email);
    await until(()=>text('.account',role),'profil '+role);
    await until(()=>text('#app h1','Demandes'),'accueil '+role);
  };
  const logout = async ()=>{
    await page(()=>[...document.querySelectorAll('.account button')].find(button=>button.textContent==='Déconnexion').click());
    await until(()=>text('#app h2','Connexion'),'déconnexion');
  };
  await send('Runtime.enable');
  await send('Page.enable');
  await send('Network.enable');
  await send('Page.navigate',{url:appOrigin});
  await login('alex','Demande');
  await until(()=>page(()=>Boolean(navigator.serviceWorker.controller)),'contrôle du service worker');
  await until(()=>text('#connection','Temps réel connecté'),'connexion SSE du navigateur');
  await route('new');
  await until(()=>text('#app h2','Nouvelle demande'),'création web');
  const title='Poste test Chrome';
  await page((title,origin)=>{
    const form=document.querySelector('#app form');
    const values={type:title,brand:'Campus',category:'computers',problemDescription:'Clavier défaillant',contactMethod:'Courriel',photoUrls:origin+'/icon-192.png'};
    for (const [name,value] of Object.entries(values)) form.elements[name].value=value;
    form.requestSubmit();
  },title,appOrigin);
  await until(()=>text('#app h1',title),'fiche créée depuis Chrome');
  const detail = await page(()=>location.hash);
  const id=detail.split('/').at(-1);
  const operation = await page(async id=>{
    const login=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'mila@example.test',password:'Demo1234!'})});
    const {token}=await login.json();
    const headers={Authorization:'Bearer '+token,'Content-Type':'application/json'};
    const users=await (await fetch('/api/users?role=repairer',{headers})).json();
    const repairer=users.find(u=>u.skills.includes('computers'));
    return (await fetch('/api/repair-requests/'+id+'/assign',{method:'PATCH',headers,body:JSON.stringify({assignedTo:repairer.id})})).status;
  },id);
  assert.equal(operation,200);
  await until(()=>text('#app .badge','Assignée'),'actualisation SSE sans rafraîchissement manuel');
  await route('dashboard');
  await until(()=>page(()=>document.querySelectorAll('meter').length>=3),'graphiques de données réelles');
  for (const width of [1280,768,390]) {
    await send('Emulation.setDeviceMetricsOverride',{width,height:850,deviceScaleFactor:1,mobile:width<500});
    assert.equal(await page(()=>document.documentElement.scrollWidth>innerWidth),false,'débordement horizontal à '+width);
  }
  const manifest = await send('Page.getAppManifest');
  assert.equal(manifest.errors.filter(error=>error.critical).length,0);
  assert.match(manifest.data,/standalone/);
  const install = await send('Page.getInstallabilityErrors');
  assert.deepEqual(install.installabilityErrors,[]);
  await send('Network.emulateNetworkConditions',{offline:true,latency:0,downloadThroughput:0,uploadThroughput:0});
  await send('Page.reload');
  await until(()=>text('#app h1','Tableau de bord'),'rechargement hors ligne PWA');
  await route(detail.slice(1));
  await until(()=>text('#app h1',title),'fiche en cache hors ligne');
  await until(()=>text('#connection','Hors ligne'),'indicateur hors ligne');
  await route('');
  await until(()=>text('#app',title),'liste en cache hors ligne');
  await logout();
  assert.equal(await page(()=>sessionStorage.getItem('reparecampus.session')),null);
  assert.equal(await text('#app',title),false);
  await until(()=>page(()=>new Promise((resolve,reject)=>{
    const req=indexedDB.open('reparecampus',1);
    req.onerror=()=>reject(req.error);
    req.onsuccess=()=>{
      const db=req.result;
      const count=db.transaction('reads').objectStore('reads').count();
      count.onsuccess=()=>{db.close();resolve(count.result===0);};
    };
  })),'effacement du cache à la déconnexion');
  await send('Network.emulateNetworkConditions',{offline:false,latency:0,downloadThroughput:-1,uploadThroughput:-1});
  for (const [email,role] of [['noah','Réparation'],['mila','Coordination'],['admin','Administration']]) {
    await login(email,role);
    await logout();
  }
  assert.deepEqual(errors,[]);
  console.log('Chrome : quatre rôles, création, actualisation SSE, graphiques, PWA installable, cache liste/fiche, purge et affichage 1280/768/390 px validés.');
} finally {
  socket?.close();
  browser.kill();
}

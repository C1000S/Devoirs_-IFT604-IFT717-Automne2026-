import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';

const origin = process.env.API_ORIGIN || 'http://127.0.0.1:3000';
const suffix = randomUUID().slice(0,8);
const demoPassword = 'Demo1234!';
async function call(path, {method='GET', token, body, status=200}={}) {
  const headers = {};
  if (token) headers.Authorization = 'Bearer '+token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(origin+'/api'+path,{headers,method,body:JSON.stringify(body),signal:AbortSignal.timeout(8000)});
  const text = await response.text();
  assert.equal(response.status,status,method+' '+path+' '+text);
  return text ? JSON.parse(text) : null;
}
const login = account => call('/auth/login',{method:'POST',body:{email:account+'@example.test',password:demoPassword}});
assert.equal((await call('/health')).status,'ok');
const preflight = await fetch(origin+'/api/parts',{method:'OPTIONS',headers:{Origin:'http://localhost:5173','Access-Control-Request-Method':'DELETE'}});
assert.match(preflight.headers.get('access-control-allow-methods'), /DELETE/);
await call('/repair-requests',{status:401});
await call('/auth/login',{method:'POST',body:{email:'alex@example.test',password:'incorrect'},status:401});
const alex = await login('alex');
const noah = await login('noah');
const mila = await login('mila');
const admin = await login('admin');
const samira = await login('samira');
const ines = await login('ines');
assert.deepEqual([alex,noah,mila,admin].map(s=>s.user.role),['requester','repairer','coordinator','admin']);
const registered = await call('/auth/register',{method:'POST',status:201,body:{name:'TP2 test',email:'tp2-'+suffix+'@example.test',password:randomUUID(),role:'admin'}});
assert.equal(registered.user.role,'requester');
const updated = await call('/auth/me',{token:registered.token,method:'PATCH',body:{name:'Profil TP2',role:'admin'}});
assert.equal(updated.name,'Profil TP2');
assert.equal(updated.role,'requester');
const create = {
  category:'computers',device:{type:'Portable '+suffix,brand:'Exemple'},
  problemDescription:'Clavier à remplacer',contactMethod:'Courriel',photos:['https://example.test/photo.png']
};
const row = await call('/repair-requests',{token:alex.token,method:'POST',body:create,status:201});
const path = '/repair-requests/'+row._id;
await call(path,{token:samira.token,status:403});
await call(path,{token:ines.token,status:403});
await call(path+'/assign',{token:alex.token,method:'PATCH',body:{assignedTo:noah.user.id},status:403});
await call(path,{token:samira.token,method:'PATCH',body:create,status:403});
await call(path,{token:alex.token,method:'PATCH',body:{contactMethod:'Par téléphone',status:'closed'}});
assert.equal((await call(path,{token:alex.token})).status,'to_triage');

const controller = new AbortController();
const timeout = setTimeout(()=>controller.abort(),15000);
try {
  const feed = await fetch(origin+'/api/events',{headers:{Authorization:'Bearer '+alex.token},signal:controller.signal});
  assert.match(feed.headers.get('content-type'), /text\/event-stream/);
  const reader = feed.body.getReader();
  const decoder = new TextDecoder();
  const first = decoder.decode((await reader.read()).value);
  assert.match(first,/event: connected/);
  const recipe = (suffix,token,body,method='PATCH',status=200)=>call(path+suffix,{token,body,method,status});
  await recipe('/assign',mila.token,{assignedTo:ines.user.id},'PATCH',400);
  assert.equal((await recipe('/assign',mila.token,{assignedTo:noah.user.id})).status,'assigned');
  const slot = (await call('/slots',{token:mila.token})).find(row=>row.availableRepairers.some(r=>r._id===noah.user.id));
  await recipe('/schedule',mila.token,{slotId:slot._id});
  const part = (await call('/parts',{token:noah.token}))[0];
  assert.equal((await recipe('/diagnosis',noah.token,{text:'Clavier endommagé',partsNeeded:[{part:part._id,quantity:1}]})).status,'in_diagnosis');
  assert.equal((await recipe('/estimate',noah.token,{cost:42.50,description:'Pièces et main-d’œuvre'})).status,'waiting');
  let events = first;
  while (!events.includes('Estimation disponible') || !events.includes('event: repair-request')) {
    const block = await reader.read();
    assert.equal(block.done,false);
    events += decoder.decode(block.value,{stream:true});
  }
  const notifications = await call('/notifications',{token:alex.token});
  const notice = notifications.find(n=>n.request===row._id && n.title==='Estimation disponible');
  assert.ok(notice);
  assert.equal((await call('/notifications/'+notice._id+'/read',{token:alex.token,method:'PATCH'})).read,true);
  assert.equal((await recipe('/estimate-response',alex.token,{accept:true})).status,'in_repair');
  await recipe('/estimate-response',alex.token,{accept:false},'PATCH',400);
  await recipe('/interventions',noah.token,{description:'Pièce remplacée',partsUsed:[{part:part._id,quantity:1}]},'POST',201);
  assert.equal((await call('/parts/'+part._id,{token:noah.token})).stock,part.stock-1);
  await recipe('/status',noah.token,{status:'ready_for_pickup'});
  const finished = await recipe('/status',mila.token,{status:'closed',reason:'Objet récupéré'});
  assert.equal(finished.status,'closed');
  const detail = await call(path,{token:alex.token});
  assert.equal(detail.interventions.length,1);
  assert.equal(detail.allowedTransitions.length,0);
  assert.ok(detail.events.length>=8);
  const demoPart = await call('/parts',{token:admin.token,method:'POST',status:201,body:{name:'Test '+suffix,sku:'TP2-'+suffix,stock:0,unitCost:5}});
  await call('/parts/'+demoPart._id,{token:admin.token,method:'DELETE',status:204});
  await call('/demo/nightly-batch',{token:admin.token,method:'POST'});
  console.log('API réelle : rôles, 401/403, profil, demandes, parcours, notifications, SSE, stock, CORS et traitement quotidien validés.');
} finally {
  clearTimeout(timeout);
  controller.abort();
}

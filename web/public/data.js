export const uid = value => typeof value === 'object' ? value?._id ?? value?.id : value;
export const states = {
  to_triage:'À trier', assigned:'Assignée', in_diagnosis:'En diagnostic', waiting:'En attente',
  in_repair:'En réparation', ready_for_pickup:'Prête à récupérer', closed:'Fermée',
  cancelled:'Annulée', not_repairable:'Non réparable'
};
export const roles = {requester:'Demande', repairer:'Réparation', coordinator:'Coordination', admin:'Administration'};
export const priorities = {low:'Basse', normal:'Normale', high:'Haute'};

const SESSION = 'reparecampus.session';
export function readSession() {
  try { return JSON.parse(sessionStorage.getItem(SESSION)); }
  catch { return null; }
}
export function saveSession(session) {
  if (session) sessionStorage.setItem(SESSION, JSON.stringify(session));
  else sessionStorage.removeItem(SESSION);
}
export function expired(session) {
  if (!session?.token || !uid(session.user)) return true;
  try {
    const payload = session.token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return Date.now() >= JSON.parse(atob(payload)).exp * 1000;
  } catch { return true; }
}

let opening;
function database() {
  opening ??= new Promise((resolve, reject) => {
    const request = indexedDB.open('reparecampus', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('reads');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  return opening;
}
async function transact(mode, run) {
  const db = await database();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('reads', mode);
    const result = run(transaction.objectStore('reads'));
    transaction.oncomplete = () => resolve(result?.result);
    transaction.onabort = () => reject(transaction.error);
    transaction.onerror = () => reject(transaction.error);
  });
}
export function clearSessionCache(session) {
  if (!session) return Promise.resolve();
  return transact('readwrite', store => {
    const range = IDBKeyRange.bound([session.cache], [session.cache, []], false, true);
    store.delete(range);
  });
}
export async function api(path, {session, method='GET', body, cache=false, signal}={}) {
  if (session && expired(session)) throw Object.assign(new Error('Session expirée.'), {status:401});
  const headers = {};
  if (session) headers.Authorization = 'Bearer ' + session.token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const key = cache && session && method === 'GET' ? [session.cache, path] : null;
  try {
    const response = await fetch('/api' + path, {
      method, headers, body:JSON.stringify(body), cache:'no-store',
      signal:signal ?? AbortSignal.timeout(8000)
    });
    if (response.status === 204) return {value:null};
    const data = await response.json();
    if (!response.ok) throw Object.assign(new Error(data.error || 'Erreur du serveur.'), {status:response.status});
    if (key && readSession()?.cache === session.cache) {
      await transact('readwrite', store => store.put({value:data, savedAt:Date.now()}, key)).catch(() => {});
    }
    return {value:data};
  } catch (error) {
    if (key && !signal?.aborted && (error instanceof TypeError || error.name === 'TimeoutError' || error.status === 502)) {
      const record = await transact('readonly', store => store.get(key)).catch(() => null);
      if (record) return {...record, offline:true};
    }
    throw error;
  }
}

export async function stream(session, signal, onEvent) {
  const response = await fetch('/api/events', {headers:{Authorization:'Bearer ' + session.token}, signal, cache:'no-store'});
  if (!response.ok) throw Object.assign(new Error('Flux indisponible'), {status:response.status});
  let remainder = '';
  for await (const chunk of response.body.pipeThrough(new TextDecoderStream())) {
    remainder += chunk;
    const frames = remainder.split(/\r?\n\r?\n/);
    remainder = frames.pop();
    for (const frame of frames) {
      const event = frame.split(/\r?\n/).find(line => line.startsWith('event:'))?.slice(6).trim();
      if (event) onEvent(event);
    }
  }
}

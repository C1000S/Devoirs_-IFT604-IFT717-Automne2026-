import {uid,roles} from './data.js';
import {h,card,panel,paragraph,field,area,select,details,form,rows,money,data} from './ui.js';

const idPath = item => encodeURIComponent(uid(item));
const choices = items => items.map(item=>[uid(item),item.name]);
const requiredChoice = options => [['','Choisir'], ...options];
const numeric = (fd, fields) => ({...data(fd),...Object.fromEntries(fields.map(name=>[name,Number(fd.get(name))]))});
const numberField = (name,label,value,min=0,step=1) => field(name,label,{type:'number',required:true,min,step,value});

export async function management(route, {get,action,mutate,queueRefresh,role}) {
  const manager = ['admin','coordinator'].includes(role);
  const admin = role === 'admin';
  if (route === 'users' && !manager) return panel('Accès refusé',paragraph('Cet écran est réservé à la coordination et à l’administration.'));
  const items = await get('/'+route);
  function remove(label,path) {
    return form(label,[],async()=>{
      if (!confirm('Confirmer la suppression ?')) return 'Suppression annulée.';
      await mutate(path,'DELETE');
      queueRefresh();
    });
  }
  if (route === 'categories') {
    const fields = (item={}) => [
      !uid(item) && field('id','Identifiant court',{pattern:'[a-z0-9-]+',required:true}),
      field('name','Nom',{value:item.name,required:true}),area('description','Description',item.description)
    ];
    return [h('h1',{},'Catégories'), manager && details('Nouvelle catégorie',action('Créer',fields(),'/categories','POST')),
      h('div',{class:'cards'},rows(items,item=>card(h('h2',{},item.name),paragraph(item.description),
        manager && details('Modifier',action('Enregistrer',fields(item),'/categories/'+idPath(item))))))];
  }
  if (route === 'parts') {
    const fields = (item={}) => [
      field('name','Nom',{value:item.name,required:true}),
      !uid(item) && field('sku','Référence SKU',{required:true}),
      numberField('unitCost','Prix catalogue ($ CA)',item.unitCost ?? 0,0,'.01'),
      numberField('stock','Stock',item.stock ?? 0)
    ];
    const payload = fd=>numeric(fd,['stock','unitCost']);
    return [h('h1',{},'Pièces'),manager && details('Nouvelle pièce',action('Ajouter',fields(),'/parts','POST',payload)),
      h('div',{class:'cards'},rows(items,item=>card(h('h2',{},item.name),paragraph(item.sku+' · '+money(item.unitCost)+' · '+item.stock+' en stock'),
        manager && details('Modifier',action('Enregistrer',fields(item),'/parts/'+idPath(item),'PATCH',payload)),
        admin && details('Supprimer la pièce',remove('Supprimer','/parts/'+idPath(item))))))];
  }
  if (route === 'slots') {
    const repairers = manager ? await get('/users?role=repairer') : [];
    const fields = (item={}) => [
      field('date','Date',{type:'date',value:item.date?.slice(0,10),required:true}),
      field('startTime','Début',{type:'time',value:item.startTime,required:true}),
      field('endTime','Fin',{type:'time',value:item.endTime,required:true}),
      numberField('capacity','Capacité',item.capacity ?? 1,1),
      area('notes','Notes',item.notes)
    ];
    function payload(fd) {
      const values = numeric(fd,['capacity']);
      if (values.endTime <= values.startTime) throw new Error('La fin doit suivre le début.');
      return values;
    }
    const list = h('div',{class:'cards'});
    const filter = h('form',{class:'filters',onsubmit:event=>event.preventDefault(),oninput:show},
      field('from','À partir du',{type:'date'}),field('to','Jusqu’au',{type:'date'}),
      manager && select('repairer','Disponibilité',[['','Toutes'],...choices(repairers)]));
    function show() {
      const f = data(new FormData(filter));
      const filtered = items.filter(s=>(!f.from || s.date.slice(0,10)>=f.from) && (!f.to || s.date.slice(0,10)<=f.to)
        && (!f.repairer || s.availableRepairers.some(u=>uid(u)===f.repairer)));
      list.replaceChildren(...rows(filtered,item=>{
        const path = '/slots/'+idPath(item);
        return card(h('h2',{},item.date.slice(0,10)+' · '+item.startTime+'–'+item.endTime),
          paragraph('Capacité '+item.capacity),paragraph('Disponibilités validées · '+(item.availableRepairers.map(u=>u.name || uid(u)).join(', ') || 'Aucune')),
          paragraph(item.notes),
          manager && details('Modifier le créneau',action('Enregistrer',fields(item),path,'PATCH',payload)),
          manager && details('Disponibilité',action('Enregistrer',[
            select('repairerId','Personne réparatrice',requiredChoice(choices(repairers)),'',true),
            select('available','Disponibilité',[['true','Valider'],['false','Retirer']],'true')
          ],path+'/availability','PATCH',fd=>({repairerId:fd.get('repairerId'),available:fd.get('available')==='true'}))),
          manager && details('Supprimer le créneau',remove('Supprimer',path)));
      }));
    }
    show();
    return [h('h1',{},'Créneaux'),manager && details('Nouveau créneau',action('Créer',fields(),'/slots','POST',payload)),panel('Filtrer',filter),list];
  }
  const categories = await get('/categories');
  const roleChoices = Object.entries(roles);
  const permissions = (item={role:'requester',skills:[]}) => [
    select('role','Rôle',roleChoices,item.role),
    select('skills','Compétences (sélection multiple)',choices(categories),item.skills,false,true)
  ];
  const payload = fd=>({role:fd.get('role'),skills:fd.getAll('skills')});
  const accountCards = h('div',{class:'cards'});
  const userFilter = h('form',{class:'filters',onsubmit:event=>event.preventDefault(),oninput:showAccounts},
    select('role','Rôle',[['','Tous'],...roleChoices]),select('skill','Compétence',[['','Toutes'],...choices(categories)]));
  function showAccounts() {
    const f = data(new FormData(userFilter));
    const filtered = items.filter(u=>(!f.role || u.role===f.role) && (!f.skill || u.skills.includes(f.skill)));
    accountCards.replaceChildren(...rows(filtered,item=>card(h('h2',{},item.name),paragraph(item.email+' · '+roles[item.role]),
      paragraph('Compétences · '+(item.skills?.join(', ') || '—')),
      admin && details('Modifier le rôle et les compétences',action('Enregistrer',permissions(item),'/users/'+idPath(item)+'/role','PATCH',payload)))));
  }
  showAccounts();
  return [h('h1',{},'Comptes'),admin && details('Créer un compte',action('Créer',[
    field('name','Nom',{required:true}),field('email','Courriel',{type:'email',required:true}),
    field('password','Mot de passe provisoire',{type:'password',minlength:8,required:true,autocomplete:'new-password'}),permissions()
  ],'/users','POST',fd=>({...data(fd),...payload(fd),password:fd.get('password')}))),
  admin && details('Traitement quotidien de démonstration',form('Exécuter le traitement',[],async()=>{
    const result = await mutate('/demo/nightly-batch','POST');
    return 'Résultat · '+Object.entries(result).map(([key,value])=>key+' '+value).join(', ');
  })),
  panel('Filtrer les comptes',userFilter),accountCards];
}

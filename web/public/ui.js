export function h(tag, attrs={}, ...children) {
  const element = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value == null || value === false) continue;
    if (key.startsWith('on')) element.addEventListener(key.slice(2), value);
    else element.setAttribute(key, value === true ? '' : String(value));
  }
  element.append(...children.flat(Infinity).filter(child => child !== null && child !== undefined && child !== false));
  return element;
}
export const link = (label, target, className) => h('a', {href:'#' + target, class:className}, label);
export const panel = (title, ...body) => h('section', {class:'panel'}, h('h2', {}, title), body);
export const paragraph = text => h('p', {}, text);
export const card = (...content) => h('article', {class:'card'}, content);
export const field = (name, label, attrs={}) => h('label', {}, label, h('input', {name, ...attrs}));
export const area = (name, label, value='', required=false) => h('label', {}, label, h('textarea', {name, required, rows:3}, value));
export function select(name, label, options, value='', required=false, multiple=false) {
  return h('label', {}, label, h('select', {name, required, multiple}, options.map(([key, text]) => h('option', {value:key, selected:multiple ? value.includes(key) : key === value}, text))));
}
export function form(label, fields, submit) {
  const message = h('div', {role:'status', class:'feedback'});
  const button = h('button', {type:'submit'}, label);
  return h('form', {onsubmit:async event => {
    event.preventDefault();
    if (button.disabled) return;
    button.disabled = true;
    message.textContent = '';
    try {
      const result = await submit(new FormData(event.currentTarget));
      message.textContent = result || 'Enregistré.';
    } catch (error) {
      message.textContent = (error.status ? error.status + ' · ' : '') + (error.message || 'Vérifiez votre connexion.');
    } finally { button.disabled = false; }
  }}, h('fieldset', {class:'fields'}, fields, h('div', {class:'submit'}, button, message)));
}
export const date = value => value ? new Date(value).toLocaleString('fr-CA') : '—';
export const money = value => new Intl.NumberFormat('fr-CA', {style:'currency',currency:'CAD'}).format(value);
export const rows = (items, render, empty='Aucun résultat.') => items.length ? items.map(render) : paragraph(empty);
export function details(title, ...body) {
  return h('details', {}, h('summary', {}, title), body);
}
export function data(formData) {
  return Object.fromEntries([...formData].map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]));
}

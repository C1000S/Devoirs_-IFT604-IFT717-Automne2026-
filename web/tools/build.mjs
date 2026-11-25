import {cp, readdir, readFile, writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';

const files = (await readdir('public')).sort();
const hash = createHash('sha256');
for (const name of files) {
  if (name.endsWith('.js')) execFileSync(process.execPath, ['--check', 'public/' + name]);
  hash.update(name).update(await readFile('public/' + name));
}
await cp('public', 'dist', {recursive:true});
const worker = await readFile('dist/sw.js', 'utf8');
await writeFile('dist/sw.js', worker.replace('development', hash.digest('hex').slice(0, 16)));
console.log('Build prêt dans dist/');

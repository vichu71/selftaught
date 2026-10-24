// PreToolUse (Bash): frena comandos peligrosos y commits con secretos en el diff.
// Exit 2 = bloquear; el mensaje de stderr llega a Claude.
import { readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { findSecret } from './patterns.mjs';

const input = JSON.parse(readFileSync(0, 'utf8'));
const command = String(input.tool_input?.command ?? '');

const RULES = [
  [/git\s+push\b.*(--force\b|--force-with-lease|\s-f\b)/, 'git push forzado: pídeselo al usuario.'],
  [/git\s+commit\b.*--no-verify/, 'No se saltan hooks de git (--no-verify).'],
  [/git\s+reset\s+--hard/, 'git reset --hard descarta trabajo: pídeselo al usuario.'],
  [/docker(?:\s+compose)?\b.*--privileged/, 'Contenedores privilegiados no permitidos.'],
  [/docker\.sock/, 'No se monta ni usa docker.sock.'],
  [/docker\s+system\s+prune/, 'docker system prune borra recursos: pídeselo al usuario.'],
  [
    /docker\s+compose\b.*\bdown\b.*(?:\s-v\b|--volumes)/,
    'down -v borra los datos (volúmenes): pídeselo al usuario.',
  ],
  [/docker\s+volume\s+(?:rm|prune)/, 'Borrar volúmenes destruye datos: pídeselo al usuario.'],
  [
    /\b(?:cat|type|less|more|head|tail|Get-Content|gc)\s+[^|;&]*\.env(?![\w.]*(?:example|sample|template))/i,
    'No se leen ficheros .env: contienen secretos.',
  ],
];

for (const [re, message] of RULES) {
  if (re.test(command)) {
    console.error(`Bloqueado: ${message}`);
    process.exit(2);
  }
}

if (/git\s+commit\b/.test(command)) {
  let diff = '';
  try {
    diff = execSync('git diff --cached -U0', {
      encoding: 'utf8',
      maxBuffer: 20 * 1024 * 1024,
      stdio: ['ignore', 'pipe', 'ignore'],
    });
  } catch {
    process.exit(0); // sin repositorio o sin cambios en el índice: nada que revisar
  }
  const added = diff
    .split('\n')
    .filter((line) => line.startsWith('+') && !line.startsWith('+++'))
    .join('\n');
  if (findSecret(added)) {
    console.error(
      'Bloqueado: el commit incluye algo parecido a una clave de API o clave privada. ' +
        'Quítalo del índice y usa variables de entorno.'
    );
    process.exit(2);
  }
}

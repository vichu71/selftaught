// PreToolUse (Write|Edit): impide escribir ficheros de secretos o claves de API en el código.
// Exit 2 = bloquear; el mensaje de stderr llega a Claude.
import { readFileSync } from 'node:fs';
import { SECRET_FILE, findSecret } from './patterns.mjs';

const input = JSON.parse(readFileSync(0, 'utf8'));
const toolInput = input.tool_input ?? {};
const path = String(toolInput.file_path ?? '').replace(/\\/g, '/');
const content = String(toolInput.content ?? toolInput.new_string ?? '');

if (SECRET_FILE.test(path)) {
  console.error(
    `Bloqueado: ${path} es un fichero de secretos. Los secretos los gestiona el usuario ` +
      'por variables de entorno; documenta las variables en .env.example.'
  );
  process.exit(2);
}

if (/(^|\/)(?:Dockerfile[^/]*|[^/]*compose[^/]*\.ya?ml)$/i.test(path) && /docker\.sock|privileged:\s*true/i.test(content)) {
  console.error('Bloqueado: no se monta docker.sock ni se usan contenedores privilegiados.');
  process.exit(2);
}

if (findSecret(content)) {
  console.error(
    'Bloqueado: el contenido parece incluir una clave de API o clave privada. ' +
      'Usa una variable de entorno y no escribas el valor en el repositorio.'
  );
  process.exit(2);
}

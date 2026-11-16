#!/usr/bin/env node
// Two-user journey E2E (H4, T10) in an environment of its own: a disposable PostgreSQL container,
// the backend jar with the default profile (the human .env is never loaded), Vite proxying to
// that backend and Playwright against all of it. Everything listens on loopback ports picked at
// random. The test restarts the backend through a loopback control endpoint of this script, so
// the backend process stays owned here and is stopped, like the container, even on failure.
//
// The owner's Compose project (service db, volume code-academy_postgres_data) is never used.
import { spawn, execFile } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { createWriteStream, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createServer } from 'node:http'
import { createServer as createTcpServer } from 'node:net'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'

const run = promisify(execFile)
const frontendDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repoDir = resolve(frontendDir, '..')
const backendDir = join(repoDir, 'backend')
const jar = join(backendDir, 'target', 'backend-0.0.1-SNAPSHOT.jar')
// The kit's two example courses, installed for this run by the real offline importer (content-import),
// in install order: «bueno» links to «ejemplo-destino». The academy's own content/ is never read.
const exampleCourses = join(repoDir, 'cursos-ejemplo')
const exampleFolders = ['bueno', 'ejemplo-destino']
const image = 'postgres:18.6'
const database = 'code_academy'
// Not «code_academy»: with PostgreSQL's default search_path ("$user", public) a role named like
// the schema V1 creates makes the second start resolve Flyway's schema to code_academy and
// abort. E2E_DB_USER=code_academy reproduces that (reported as a backend defect in T10).
const dbUser = process.env.E2E_DB_USER ?? 'journey'

const stamp = new Date().toISOString().replace(/[:.]/g, '-')
// Under frontend/test-results/, ignored by Git. One folder per run: nothing is ever deleted.
const runDir = join(frontendDir, 'test-results', 'journey', stamp)
const logDir = join(runDir, 'logs')
const screenshotDir = join(runDir, 'screenshots')
const contentDir = join(runDir, 'content')
const container = `code-academy-e2e-${randomBytes(4).toString('hex')}`
// Generated per run, passed only through child environments, never printed or written.
const dbPassword = randomBytes(24).toString('hex')
const controlToken = randomBytes(24).toString('hex')

const started = Date.now()
const timings = {}
const log = (message) => console.log(`[journey ${((Date.now() - started) / 1000).toFixed(1)}s] ${message}`)
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

async function timed(name, action) {
  const from = Date.now()
  try {
    return await action()
  } finally {
    timings[name] = Date.now() - from
  }
}

function freePort() {
  return new Promise((done, fail) => {
    const server = createTcpServer()
    server.unref()
    server.on('error', fail)
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address()
      server.close(() => done(port))
    })
  })
}

async function docker(...args) {
  const { stdout } = await run('docker', args, { env: { ...process.env, POSTGRES_PASSWORD: dbPassword } })
  return stdout.trim()
}

async function startDatabase(port) {
  await docker('image', 'inspect', '--format', '{{.Id}}', image)
  // -e POSTGRES_PASSWORD without a value: Docker takes it from this process's environment.
  // Data lives in a tmpfs: no volume is created, and --rm removes the container when stopped.
  await docker(
    'run', '--rm', '-d', '--pull', 'never', '--name', container,
    '--label', 'dev.codeacademy.e2e=journey',
    '-p', `127.0.0.1:${port}:5432`,
    '--tmpfs', '/var/lib/postgresql',
    '-e', 'POSTGRES_PASSWORD', '-e', `POSTGRES_USER=${dbUser}`, '-e', `POSTGRES_DB=${database}`,
    image,
  )
  // TCP readiness: the image's init server listens on the Unix socket only.
  for (let attempt = 0; attempt < 120; attempt += 1) {
    try {
      await docker('exec', container, 'pg_isready', '-q', '-h', '127.0.0.1', '-U', dbUser, '-d', database)
      return
    } catch {
      await sleep(500)
    }
  }
  throw new Error(`PostgreSQL in ${container} did not become ready`)
}

async function stopDatabase() {
  try {
    await docker('stop', '-t', '10', container)
  } catch (error) {
    log(`docker stop ${container}: ${error.message.split('\n')[0]}`)
  }
  const left = await docker('ps', '-a', '--filter', `name=^${container}$`, '--format', '{{.Names}}')
  log(left === '' ? `container ${container} removed` : `WARNING: container still present: ${left}`)
  return left === ''
}

/** The backend environment: inherited variables minus anything that could select a profile or a database. */
function backendEnvironment(dbPort) {
  const env = {}
  for (const [key, value] of Object.entries(process.env)) {
    if (/^(SPRING_|DB_|CODE_ACADEMY_|POSTGRES_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$)/.test(key)) continue
    env[key] = value
  }
  return {
    ...env,
    SPRING_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${dbPort}/${database}`,
    SPRING_DATASOURCE_USERNAME: dbUser,
    SPRING_DATASOURCE_PASSWORD: dbPassword,
  }
}

/** Fixed identity and dates, and nothing from the machine's Git configuration: the same commits on every run. */
const gitEnvironment = {
  ...process.env,
  GIT_CONFIG_GLOBAL: '/dev/null', GIT_CONFIG_NOSYSTEM: '1',
  GIT_AUTHOR_NAME: 'Fixture', GIT_AUTHOR_EMAIL: 'fixture@example.invalid',
  GIT_COMMITTER_NAME: 'Fixture', GIT_COMMITTER_EMAIL: 'fixture@example.invalid',
  GIT_AUTHOR_DATE: '2026-01-01T00:00:00 +0000', GIT_COMMITTER_DATE: '2026-01-01T00:00:00 +0000',
}

/**
 * Installs the example courses the way an academy does: each one committed, as it is, to a repository of its own
 * (the folder itself is never touched) and generated with content-import on top of the previous copy.
 */
async function installExampleCourses() {
  const work = join(runDir, 'content-import')
  let active = join(work, 'empty')
  mkdirSync(active, { recursive: true })
  for (const [index, folder] of exampleFolders.entries()) {
    const repository = join(work, `${folder}.git`)
    const git = (...args) => run('git', [`--git-dir=${repository}`, ...args], { env: gitEnvironment })
    await run('git', ['init', '--bare', '--quiet', repository], { env: gitEnvironment })
    await git(`--work-tree=${join(exampleCourses, folder)}`, 'add', '--all')
    await git(`--work-tree=${join(exampleCourses, folder)}`, 'commit', '--quiet', '--message=ejemplo')
    // Installed the way an academy installs a published version: by its tag, v + the version of curso.json.
    const version = JSON.parse(readFileSync(join(exampleCourses, folder, 'curso.json'), 'utf8')).version
    const tag = `v${version}`
    await git('tag', tag)
    const output = index === exampleFolders.length - 1 ? contentDir : join(work, `content-${index + 1}`)
    const { stdout } = await run('java', ['-Dloader.main=dev.codeacademy.content.ContentImportCli', '-cp', jar,
      'org.springframework.boot.loader.launch.PropertiesLauncher', 'generate', '--active', active, '--source', repository,
      '--tag', tag, '--output', output, '--report', join(logDir, `content-import-${folder}.json`)])
    log(`${folder}: ${stdout.trim()}`)
    active = output
  }
}

function createBackend(dbPort, port) {
  let current = null
  let generation = 0

  async function start() {
    generation += 1
    const out = createWriteStream(join(logDir, `backend-${generation}.log`))
    const child = spawn('java', [
      '-jar', jar,
      '--server.address=127.0.0.1', `--server.port=${port}`, `--code-academy.content-dir=${contentDir}`,
    ], { cwd: backendDir, env: backendEnvironment(dbPort), stdio: ['ignore', 'pipe', 'pipe'] })
    child.stdout.pipe(out)
    child.stderr.pipe(out)
    const exited = new Promise((done) => child.once('exit', (code, signal) => done({ code, signal })))
    current = { child, exited, pid: child.pid, generation }
    const health = `http://127.0.0.1:${port}/actuator/health`
    for (let attempt = 0; attempt < 240; attempt += 1) {
      if (child.exitCode !== null || child.signalCode !== null) break
      try {
        const response = await fetch(health)
        if (response.ok && (await response.json()).status === 'UP') {
          log(`backend #${generation} up (pid ${child.pid}, port ${port})`)
          return { pid: child.pid, generation }
        }
      } catch {
        // Not listening yet.
      }
      await sleep(500)
    }
    throw new Error(`backend #${generation} did not become healthy; see ${join(logDir, `backend-${generation}.log`)}`)
  }

  async function stop() {
    if (!current) return null
    const { child, exited, pid, generation: stopped } = current
    current = null
    if (child.exitCode === null && child.signalCode === null) child.kill('SIGTERM')
    let timer
    const result = await Promise.race([exited, new Promise((done) => {
      timer = setTimeout(() => done(null), 30_000)
    })])
    clearTimeout(timer)
    if (result === null) {
      child.kill('SIGKILL')
      const killed = await exited
      log(`WARNING: backend #${stopped} ignored SIGTERM for 30 s and was killed`)
      return { pid, generation: stopped, ...killed, forced: true }
    }
    log(`backend #${stopped} (pid ${pid}) exited: code ${result.code}, signal ${result.signal}`)
    return { pid, generation: stopped, ...result, forced: false }
  }

  return { start, stop }
}

/** POST /backend/restart: SIGTERM, wait for exit, start again against the same database. */
function controlServer(backend) {
  const restarts = []
  const server = createServer(async (request, response) => {
    if (request.method !== 'POST' || request.url !== '/backend/restart' || request.headers['x-e2e-token'] !== controlToken) {
      response.writeHead(404).end()
      return
    }
    try {
      const stopped = await timed(`backendRestart${restarts.length + 1}`, async () => {
        const result = await backend.stop()
        return { stopped: result, started: await backend.start() }
      })
      restarts.push(stopped)
      response.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(stopped))
    } catch (error) {
      response.writeHead(500, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: error.message }))
    }
  })
  return new Promise((done) => server.listen(0, '127.0.0.1', () => done({ server, port: server.address().port, restarts })))
}

function playwright(env) {
  return new Promise((done) => {
    const child = spawn(join(frontendDir, 'node_modules', '.bin', 'playwright'), ['test', '--config', 'playwright.config.ts'], {
      cwd: frontendDir, env, stdio: 'inherit',
    })
    child.once('exit', (code, signal) => done(code ?? (signal ? 1 : 0)))
  })
}

async function main() {
  if (!existsSync(jar)) {
    console.error(`Missing ${jar}. Build it first from backend/: ./mvnw -B package -DskipTests`)
    return 2
  }
  mkdirSync(logDir, { recursive: true })
  mkdirSync(screenshotDir, { recursive: true })
  const [dbPort, backendPort, frontendPort] = [await freePort(), await freePort(), await freePort()]
  log(`run folder ${runDir}`)
  log(`ports: postgres ${dbPort}, backend ${backendPort}, frontend ${frontendPort}; container ${container}`)

  const backend = createBackend(dbPort, backendPort)
  let control = null
  let exitCode = 1
  let databaseStarted = false
  const cleanup = async () => {
    await timed('teardown', async () => {
      await backend.stop()
      if (control) await new Promise((done) => control.server.close(done))
      if (databaseStarted) await stopDatabase()
    })
  }
  const interrupt = (signal) => {
    log(`${signal}: tearing down`)
    cleanup().finally(() => process.exit(130))
  }
  process.once('SIGINT', interrupt)
  process.once('SIGTERM', interrupt)

  try {
    await timed('content', installExampleCourses)
    databaseStarted = true
    await timed('database', () => startDatabase(dbPort))
    log('postgres ready')
    await timed('backendStart', () => backend.start())
    control = await controlServer(backend)

    const env = {}
    for (const [key, value] of Object.entries(process.env)) if (!/^(DB_|POSTGRES_|SPRING_)/.test(key)) env[key] = value
    Object.assign(env, {
      E2E_FRONTEND_URL: `http://127.0.0.1:${frontendPort}`,
      E2E_BACKEND_URL: `http://127.0.0.1:${backendPort}`,
      E2E_CONTROL_URL: `http://127.0.0.1:${control.port}`,
      E2E_CONTROL_TOKEN: controlToken,
      E2E_RUN_DIR: runDir,
      E2E_CONTENT_DIR: contentDir,
      E2E_SCREENSHOT_DIR: screenshotDir,
    })
    exitCode = await timed('playwright', () => playwright(env))
  } catch (error) {
    console.error(`[journey] ${error.message}`)
    exitCode = 1
  } finally {
    await cleanup()
    timings.total = Date.now() - started
    const summary = { exitCode, container, ports: { dbPort, backendPort, frontendPort }, restarts: control?.restarts ?? [], timingsMs: timings }
    writeFileSync(join(runDir, 'summary.json'), `${JSON.stringify(summary, null, 2)}\n`)
    log(`timings (ms): ${JSON.stringify(timings)}`)
    log(`screenshots: ${screenshotDir}`)
    log(exitCode === 0 ? 'journey passed' : `journey FAILED (exit ${exitCode})`)
  }
  return exitCode
}

process.exitCode = await main()

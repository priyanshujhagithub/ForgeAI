import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './api'
import { diffFile } from './diff'
import {
  formatCost,
  formatDuration,
  parseServerDate,
  secondsBetween,
  shortSha,
  timeAgo,
} from './format'
import { STEPS, stepIndex } from './runStatus'
import { clearSession, onSessionChange, readSession, writeSession } from './session'

function token(claims: object): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS512' })}.${encode(claims)}.signature`
}

const inOneHour = () => Math.floor(Date.now() / 1000) + 3600

describe('diffFile', () => {
  it('numbers both sides and counts added and removed lines', () => {
    const diff = diffFile('a\nb\nc\n', 'a\nB\nc\nd\n')

    expect(diff.added).toBe(2)
    expect(diff.removed).toBe(1)
    expect(diff.hunks).toHaveLength(1)
    expect(diff.hunks[0].lines).toEqual([
      { kind: 'context', text: 'a', oldNumber: 1, newNumber: 1 },
      { kind: 'remove', text: 'b', oldNumber: 2, newNumber: null },
      { kind: 'add', text: 'B', oldNumber: null, newNumber: 2 },
      { kind: 'context', text: 'c', oldNumber: 3, newNumber: 3 },
      { kind: 'add', text: 'd', oldNumber: null, newNumber: 4 },
    ])
  })

  it('treats a new file as all additions', () => {
    const diff = diffFile('', 'one\ntwo\n')

    expect(diff.added).toBe(2)
    expect(diff.removed).toBe(0)
    expect(diff.hunks[0].lines.every((line) => line.kind === 'add')).toBe(true)
  })

  it('has no hunks when nothing changed', () => {
    expect(diffFile('same\n', 'same\n').hunks).toEqual([])
  })

  it('does not show the missing-newline marker as a line of code', () => {
    const diff = diffFile('a', 'b')

    expect(diff.hunks[0].lines.map((line) => line.text)).toEqual(['a', 'b'])
  })
})

describe('format', () => {
  it('reads API timestamps as UTC even though they carry no zone', () => {
    expect(parseServerDate('2026-10-11T16:24:05.123').toISOString()).toBe(
      '2026-10-11T16:24:05.123Z',
    )
    expect(parseServerDate('2026-10-11T16:24:05Z').toISOString()).toBe('2026-10-11T16:24:05.000Z')
  })

  it('describes how long ago something happened', () => {
    const now = new Date('2026-10-11T12:00:00Z')

    expect(timeAgo('2026-10-11T11:59:40', now)).toBe('just now')
    expect(timeAgo('2026-10-11T11:18:00', now)).toBe('42 minutes ago')
    expect(timeAgo('2026-10-11T09:00:00', now)).toBe('3 hours ago')
    expect(timeAgo('2026-10-10T12:00:00', now)).toBe('yesterday')
  })

  it('formats durations, costs and commit ids', () => {
    expect(formatDuration(48.2)).toBe('48 s')
    expect(formatDuration(245)).toBe('4 min 05 s')
    expect(formatCost(0.00093)).toBe('$0.0009')
    expect(formatCost(1.5)).toBe('$1.50')
    expect(shortSha('0d0c23adcea4468b3e35e091147c75660c3340a7')).toBe('0d0c23a')
  })

  it('measures a running run against now and a finished one against its end', () => {
    const now = new Date('2026-10-11T12:05:00Z')

    expect(secondsBetween('2026-10-11T12:00:00', null, now)).toBe(300)
    expect(secondsBetween('2026-10-11T12:00:00', '2026-10-11T12:04:05', now)).toBe(245)
  })
})

describe('stepIndex', () => {
  it('follows a run along the happy path', () => {
    expect(stepIndex('QUEUED', false, false)).toBe(0)
    expect(stepIndex('GENERATING_AND_TESTING', true, true)).toBe(3)
    expect(stepIndex('COMPLETED', true, true)).toBe(STEPS.length)
  })

  it('places a run that ended badly at the step where it stopped', () => {
    expect(stepIndex('TESTS_FAILED', true, true)).toBe(3)
    expect(stepIndex('SANDBOX_UNAVAILABLE', true, true)).toBe(3)
    // A plain failure is located by what the run managed to produce.
    expect(stepIndex('FAILED', false, false)).toBe(1)
    expect(stepIndex('FAILED', false, true)).toBe(2)
    expect(stepIndex('FAILED', true, true)).toBe(3)
  })
})

describe('session', () => {
  it('keeps a valid token and exposes its claims', () => {
    const session = writeSession(token({ email: 'ada@example.com', exp: inOneHour() }))

    expect(session?.email).toBe('ada@example.com')
    expect(readSession()?.email).toBe('ada@example.com')
  })

  it('discards an expired token instead of treating the user as signed in', () => {
    localStorage.setItem('forgeai.token', token({ email: 'ada@example.com', exp: 1 }))

    expect(readSession()).toBeNull()
    expect(localStorage.getItem('forgeai.token')).toBeNull()
  })

  it('discards a value that is not a token', () => {
    localStorage.setItem('forgeai.token', 'not-a-jwt')

    expect(readSession()).toBeNull()
  })

  it('tells listeners when the session starts and ends', () => {
    const seen: (string | null)[] = []
    const stop = onSessionChange((session) => seen.push(session?.email ?? null))

    writeSession(token({ email: 'ada@example.com', exp: inOneHour() }))
    clearSession()
    stop()
    clearSession()

    expect(seen).toEqual(['ada@example.com', null])
  })
})

describe('api', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  const respond = (status: number, body?: unknown) =>
    fetchMock.mockResolvedValueOnce(
      new Response(body === undefined ? null : JSON.stringify(body), { status }),
    )

  it('sends the bearer token on authenticated calls', async () => {
    const value = token({ email: 'ada@example.com', exp: inOneHour() })
    writeSession(value)
    respond(200, [])

    await api.projects()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/projects')
    expect(init.headers.Authorization).toBe(`Bearer ${value}`)
  })

  it('never sends a token when signing in', async () => {
    writeSession(token({ email: 'ada@example.com', exp: inOneHour() }))
    respond(200, { accessToken: 'new' })

    await api.login('ada@example.com', 'pw')

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('turns a problem document into an error with the API’s own wording', async () => {
    writeSession(token({ email: 'ada@example.com', exp: inOneHour() }))
    respond(409, { status: 409, detail: 'Project 5 already has a run in progress' })

    const error = await api.startRun(5, 'task').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe('Project 5 already has a run in progress')
  })

  it('ends the session when the API rejects the token', async () => {
    writeSession(token({ email: 'ada@example.com', exp: inOneHour() }))
    respond(401)

    await expect(api.projects()).rejects.toMatchObject({ status: 401 })
    expect(readSession()).toBeNull()
  })

  it('keeps the session when a sign-in attempt is rejected', async () => {
    writeSession(token({ email: 'ada@example.com', exp: inOneHour() }))
    respond(401, { detail: 'Invalid email or password' })

    await expect(api.login('ada@example.com', 'wrong')).rejects.toMatchObject({
      message: 'Invalid email or password',
    })
    expect(readSession()).not.toBeNull()
  })

  it('reports an unreachable server in plain words', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await expect(api.login('a@b.com', 'pw')).rejects.toMatchObject({
      status: 0,
      message: 'Cannot reach the ForgeAI server. Check that it is running.',
    })
  })

  it('asks for a file at the commit the run started from', async () => {
    writeSession(token({ email: 'ada@example.com', exp: inOneHour() }))
    respond(200, { path: 'app/main.py', content: '', sha: 's' })

    await api.file(5, 'app/main.py', 'abc1234')

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/v1/github/projects/5/file?path=app%2Fmain.py&ref=abc1234',
    )
  })

  it('handles an empty 204 reply', async () => {
    writeSession(token({ email: 'ada@example.com', exp: inOneHour() }))
    respond(204)

    await expect(api.deleteProject(5)).resolves.toBeUndefined()
  })
})

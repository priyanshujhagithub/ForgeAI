import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { activeRun, completedRun, failedRun, mockApi, signIn } from './mockApi'

// The real app in a real browser, at desktop and phone sizes, against a mocked API.

const PAGES = [
  '/',
  '/login',
  '/register',
  '/app',
  '/app/projects/new',
  '/app/projects/5',
  `/app/runs/${completedRun.id}`,
  `/app/runs/${completedRun.id}?tab=changes`,
  `/app/runs/${completedRun.id}?tab=attempts`,
  `/app/runs/${completedRun.id}?tab=analysis`,
  `/app/runs/${failedRun.id}`,
  `/app/runs/${activeRun.id}`,
  '/app/settings',
  '/app/does-not-exist',
]

test.describe('layout', () => {
  for (const path of PAGES) {
    // Wide content (diffs, test output) must scroll inside its own box.
    test(`${path} never scrolls sideways`, async ({ page }) => {
      if (path.startsWith('/app')) await signIn(page)
      await mockApi(page)
      await page.goto(path)
      await page.waitForLoadState('networkidle')

      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow).toBeLessThanOrEqual(0)
    })
  }
})

test.describe('signing in', () => {
  test('a protected page asks for sign-in, then returns to where you were going', async ({
    page,
  }) => {
    await mockApi(page)
    await page.goto('/app/projects/5')

    await expect(page).toHaveURL(/\/login$/)
    await page.getByLabel('Email').fill('ada@example.com')
    await page.getByLabel('Password').fill('correct-horse')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page).toHaveURL(/\/app\/projects\/5$/)
    await expect(page.getByRole('heading', { name: 'Registration service' })).toBeVisible()
  })

  test('a wrong password shows the reason and stays on the form', async ({ page }) => {
    await mockApi(page)
    await page.goto('/login')

    await page.getByLabel('Email').fill('ada@example.com')
    await page.getByLabel('Password').fill('wrong-password')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByRole('alert')).toContainText('Invalid email or password')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('registration rejects a short password before calling the server', async ({ page }) => {
    await mockApi(page)
    let registered = false
    page.on('request', (request) => {
      if (request.url().includes('/auth/register')) registered = true
    })
    await page.goto('/register')

    await page.getByLabel('Email').fill('ada@example.com')
    await page.getByLabel('Password').fill('short')
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page.getByText('Use at least 8 characters.')).toBeVisible()
    expect(registered).toBe(false)
  })

  test('signing out returns to the sign-in page and forgets the session', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto('/app')

    await page.getByRole('button', { name: 'Sign out' }).click()

    await expect(page).toHaveURL(/\/login$/)
    expect(await page.evaluate(() => localStorage.getItem('forgeai.token'))).toBeNull()
  })
})

test.describe('working with runs', () => {
  test('an example fills the task, and starting a run opens it', async ({ page }) => {
    await signIn(page)
    await mockApi(page, { runs: [completedRun] })
    await page.goto('/app/projects/5')

    await page.getByRole('button', { name: /Add GET \/health/ }).click()
    await expect(page.getByLabel('Task')).toHaveValue(/Add GET \/health/)
    await page.getByRole('button', { name: 'Start run' }).click()

    await expect(page).toHaveURL(new RegExp(`/app/runs/${activeRun.id}$`))
    await expect(page.getByText('In progress', { exact: true })).toBeVisible()
    await expect(page.getByRole('listitem').filter({ hasText: 'Write and test' })).toHaveAttribute(
      'aria-current',
      'step',
    )
  })

  test('a project with a run in progress links to it instead of offering a second one', async ({
    page,
  }) => {
    await signIn(page)
    await mockApi(page, { runs: [activeRun, completedRun] })
    await page.goto('/app/projects/5')

    await expect(page.getByText('A run is in progress')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Start run' })).toHaveCount(0)
  })

  test('a finished run shows the diff against the commit it started from', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    const fileRequests: string[] = []
    page.on('request', (request) => {
      if (request.url().includes('/file?')) fileRequests.push(request.url())
    })

    await page.goto(`/app/runs/${completedRun.id}?tab=changes`)

    await expect(page.getByText('user = store.add(request.email.strip().lower()')).toBeVisible()
    await expect(page.getByRole('link', { name: /Review pull request #2/ })).toHaveAttribute(
      'href',
      completedRun.pullRequestUrl,
    )
    // Only the modified file needs its original; the new file has none.
    expect(fileRequests).toHaveLength(1)
    expect(fileRequests[0]).toContain(`ref=${completedRun.baseSha}`)
  })

  test('tabs work from the keyboard and survive a reload', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto(`/app/runs/${completedRun.id}`)

    await page.getByRole('tab', { name: 'Plan' }).focus()
    await page.keyboard.press('ArrowRight')
    await expect(page.getByRole('tab', { name: /Changes/ })).toHaveAttribute('aria-selected', 'true')
    await page.keyboard.press('End')
    await expect(page.getByRole('tab', { name: 'Repository analysis' })).toBeFocused()
    await expect(page).toHaveURL(/tab=analysis/)

    await page.reload()
    await expect(page.getByText('Python FastAPI service')).toBeVisible()
  })

  test('a failed run says plainly that nothing was proposed', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto(`/app/runs/${failedRun.id}?tab=changes`)

    await expect(page.getByText('so no pull request was opened').first()).toBeVisible()
    await expect(page.getByText('These changes were not proposed')).toBeVisible()
    await expect(page.getByRole('link', { name: /Review pull request/ })).toHaveCount(0)
  })
})

test.describe('view transitions', () => {
  /** Counts calls to document.startViewTransition without changing what it does. */
  async function countTransitions(page: Page) {
    await page.addInitScript(() => {
      const counter = window as unknown as { transitions: number }
      counter.transitions = 0
      const original = document.startViewTransition.bind(document)
      document.startViewTransition = ((...args: Parameters<typeof original>) => {
        counter.transitions++
        return original(...args)
      }) as typeof document.startViewTransition
    })
    return () => page.evaluate(() => (window as unknown as { transitions: number }).transitions)
  }

  test('moving between pages runs a view transition', async ({ page }) => {
    const transitions = await countTransitions(page)
    await signIn(page)
    await mockApi(page)
    await page.goto('/app')

    await page.getByRole('link', { name: /Registration service/ }).click()
    await expect(page.getByRole('heading', { name: 'Registration service' })).toBeVisible()
    expect(await transitions()).toBe(1)

    await page.getByRole('link', { name: /Store and return email addresses/ }).click()
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Store and return')
    expect(await transitions()).toBe(2)
  })

  test('the opened item shares its title with the next page, and only that item', async ({
    page,
  }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto('/app')
    await page.getByText('Billing API').waitFor()

    // At rest no card carries the name: two elements sharing it would cancel the transition.
    expect(await page.locator('.vt-project-title').count()).toBe(0)

    await page.getByRole('link', { name: /Registration service/ }).click()
    await expect(page.locator('h1.vt-project-title')).toHaveText('Registration service')
  })
})

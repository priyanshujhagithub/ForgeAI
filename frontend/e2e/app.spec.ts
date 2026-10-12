import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { activeRun, completedRun, failedRun, mockApi, signIn, untestedRun } from './mockApi'

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
  `/app/runs/${untestedRun.id}?tab=attempts`,
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

  test('a repository without tests gets a pull request that is clearly untested', async ({
    page,
  }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto(`/app/runs/${untestedRun.id}?tab=attempts`)

    await expect(page.getByText('Pull request open, untested')).toBeVisible()
    await expect(page.getByText('could not be verified').first()).toBeVisible()
    await expect(page.getByRole('link', { name: /Check pull request #\s*3/ })).toBeVisible()
    await expect(page.getByText('No tests were run.')).toBeVisible()
    // Every step completed: this is not shown as a failure.
    await expect(page.getByText('(stopped here)')).toHaveCount(0)

    await page.getByRole('tab', { name: /Changes/ }).click()
    await expect(page.getByText('These changes were not proposed')).toHaveCount(0)
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

test.describe('motion', () => {
  const direction = (page: Page) => page.evaluate(() => document.documentElement.dataset.nav)

  test('pages move in the direction you are going', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto('/app')

    await page.getByRole('link', { name: /Registration service/ }).click()
    await expect(page.getByRole('heading', { name: 'Registration service' })).toBeVisible()
    expect(await direction(page)).toBe('forward')

    await page.getByRole('main').getByRole('link', { name: 'Projects' }).click()
    await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible()
    expect(await direction(page)).toBe('back')

    // Settings is beside Projects, not beneath it.
    await page.getByRole('link', { name: 'Settings' }).click()
    await expect(page.getByRole('heading', { name: 'Settings', level: 1 })).toBeVisible()
    expect(await direction(page)).toBe('level')
  })

  test('one line marks the current section and moves with it', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto('/app/projects/5')

    // A project page is still inside "Projects".
    const projects = page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Projects' })
    const settings = page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Settings' })
    await expect(projects.locator('.vt-nav-indicator')).toHaveCount(1)

    await settings.click()
    await expect(settings.locator('.vt-nav-indicator')).toHaveCount(1)
    // Two elements sharing the name would cancel the transition.
    await expect(page.locator('.vt-nav-indicator')).toHaveCount(1)
  })

  test('the line under the tabs follows the selected tab', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto(`/app/runs/${completedRun.id}`)

    const tabs = page.getByRole('tablist')
    const line = () =>
      tabs.evaluate((element) => ({
        x: element.style.getPropertyValue('--line-x'),
        width: element.style.getPropertyValue('--line-width'),
      }))

    await expect(tabs).toHaveAttribute('data-placed', 'true')
    const underPlan = await line()
    expect(underPlan.x).toBe('0px')

    const changes = page.getByRole('tab', { name: /Changes/ })
    await changes.click()
    const underChanges = await line()
    expect(underChanges.x).toBe(`${await changes.evaluate((tab) => (tab as HTMLElement).offsetLeft)}px`)
    expect(underChanges.x).not.toBe(underPlan.x)
  })

  test('switching tabs keeps your place on the page', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    await page.goto(`/app/runs/${completedRun.id}?tab=attempts`)
    await page.getByText('Root cause').waitFor()

    const tab = page.getByRole('tab', { name: /Changes/ })
    await tab.scrollIntoViewIfNeeded()
    await page.evaluate(() => window.scrollBy({ top: 120, behavior: 'instant' }))
    const before = await page.evaluate(() => window.scrollY)
    expect(before).toBeGreaterThan(0)

    await tab.click()
    await expect(page).toHaveURL(/tab=changes/)
    await expect(page.getByText('tests/test_normalise.py').first()).toBeVisible()
    // Not back at the top. (It may be a little less than before: the new
    // panel can be shorter, and a page cannot stay scrolled past its end.)
    const after = await page.evaluate(() => window.scrollY)
    expect(after).toBeGreaterThan(0)
    expect(after).toBeLessThanOrEqual(before)
  })

  test('a run that finishes while you watch updates without a reload', async ({ page }) => {
    await signIn(page)
    await mockApi(page)
    // In progress the first time it is asked for, finished after that.
    let asked = 0
    await page.route(`**/api/v1/runs/${activeRun.id}`, (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(asked++ === 0 ? activeRun : { ...completedRun, id: activeRun.id }),
      }),
    )
    await page.goto(`/app/runs/${activeRun.id}`)

    const writing = page.getByRole('listitem').filter({ hasText: 'Write and test' })
    await expect(writing).toHaveAttribute('aria-current', 'step')

    await expect(page.getByRole('link', { name: /Review pull request #2/ })).toBeVisible({
      timeout: 10_000,
    })
    await expect(writing).toContainText('(done)')
    await expect(page.getByText('In progress', { exact: true })).toHaveCount(0)
  })

  test('the example on the home page plays through to a pull request', async ({ page }) => {
    await mockApi(page)
    await page.goto('/')

    const example = page.getByRole('figure')
    await expect(example.getByText('Running', { exact: true })).toBeVisible()
    await expect(example.getByText('Pull request open')).toBeVisible({ timeout: 12_000 })
    await expect(example.getByText('4 min 05 s')).toBeVisible()

    await example.getByRole('button', { name: /Replay/ }).click()
    await expect(example.getByText('Running', { exact: true })).toBeVisible()
  })

  test('sections further down the home page arrive as they are scrolled to', async ({ page }) => {
    await mockApi(page)
    await page.goto('/')

    const results = page.locator('#results [data-reveal]').first()
    await expect(results).toHaveAttribute('data-reveal-state', 'pending')
    await results.scrollIntoViewIfNeeded()
    await expect(results).toHaveAttribute('data-reveal-state', 'shown')
  })

  test('with reduced motion nothing waits to be animated in', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await mockApi(page)
    await page.goto('/')

    await expect(page.locator('[data-reveal-state]')).toHaveCount(0)
    // The example is shown finished instead of being played.
    await expect(page.getByRole('figure').getByText('Pull request open')).toBeVisible()
    await expect(page.getByRole('button', { name: /Replay/ })).toHaveCount(0)
  })
})

test.describe('theme', () => {
  const theme = (page: Page) => page.evaluate(() => document.documentElement.dataset.theme)
  const paper = (page: Page) =>
    page.evaluate(() => getComputedStyle(document.body).backgroundColor)

  test('the toggle switches theme and the choice survives a reload', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' })
    await signIn(page)
    await mockApi(page)
    await page.goto('/app')
    const light = await paper(page)

    await page.getByRole('button', { name: 'Switch to dark theme' }).click()
    expect(await theme(page)).toBe('dark')
    await expect(page.getByRole('button', { name: 'Switch to light theme' })).toBeVisible()
    expect(await paper(page)).not.toBe(light)

    await page.reload()
    expect(await theme(page)).toBe('dark')
    expect(await paper(page)).not.toBe(light)
  })

  test('"System" in settings hands the choice back to the device', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await signIn(page)
    await mockApi(page)
    await page.goto('/app/settings')
    const dark = await paper(page)

    // The radio buttons are drawn as one control; what you press is the label.
    const appearance = page.getByRole('radiogroup', { name: 'Appearance' })
    await appearance.getByText('Light').click()
    await expect(appearance.getByRole('radio', { name: 'Light' })).toBeChecked()
    expect(await theme(page)).toBe('light')
    expect(await paper(page)).not.toBe(dark)

    await appearance.getByText('System').click()
    await expect(appearance.getByRole('radio', { name: 'System' })).toBeChecked()
    expect(await theme(page)).toBeUndefined()
    expect(await paper(page)).toBe(dark)
  })
})

import { test } from '@playwright/test'
import { activeRun, completedRun, failedRun, mockApi, signIn, untestedRun } from './mockApi'

// Captures every page at desktop and phone size, in light and dark, for visual
// review. Run with: npx playwright test screenshots
// Output goes to e2e/screenshots/ (not committed).

const shot = (name: string) => `e2e/screenshots/${name}`

for (const scheme of ['light', 'dark'] as const) {
  test.describe(`${scheme} theme`, () => {
    test.use({ colorScheme: scheme })

    test('landing', async ({ page }, info) => {
      await mockApi(page)
      await page.goto('/')
      await page.evaluate(() => document.fonts.ready)
      await page.screenshot({ path: shot(`${info.project.name}-${scheme}-landing.png`), fullPage: true })
    })

    test('sign in', async ({ page }, info) => {
      await mockApi(page)
      await page.goto('/login')
      await page.evaluate(() => document.fonts.ready)
      await page.screenshot({ path: shot(`${info.project.name}-${scheme}-login.png`) })
    })

    test('projects', async ({ page }, info) => {
      await signIn(page)
      await mockApi(page)
      await page.goto('/app')
      await page.getByText('Registration service').waitFor()
      await page.getByText('Pull request open').waitFor()
      await page.screenshot({ path: shot(`${info.project.name}-${scheme}-projects.png`), fullPage: true })
    })

    test('project', async ({ page }, info) => {
      await signIn(page)
      await mockApi(page)
      await page.goto('/app/projects/5')
      await page.getByRole('heading', { name: 'Runs' }).waitFor()
      await page.getByText('Tests failed').waitFor()
      await page.screenshot({ path: shot(`${info.project.name}-${scheme}-project.png`), fullPage: true })
    })

    test('run: completed, changes tab', async ({ page }, info) => {
      await signIn(page)
      await mockApi(page)
      await page.goto(`/app/runs/${completedRun.id}?tab=changes`)
      await page.getByText('tests/test_normalise.py').first().waitFor()
      await page.getByText('@@').first().waitFor()
      await page.screenshot({ path: shot(`${info.project.name}-${scheme}-run-changes.png`), fullPage: true })
    })
  })
}

test('run: completed, attempts tab', async ({ page }, info) => {
  await signIn(page)
  await mockApi(page)
  await page.goto(`/app/runs/${completedRun.id}?tab=attempts`)
  await page.getByText('Root cause').waitFor()
  await page.screenshot({ path: shot(`${info.project.name}-light-run-attempts.png`), fullPage: true })
})

test('run: in progress', async ({ page }, info) => {
  await signIn(page)
  await mockApi(page)
  await page.goto(`/app/runs/${activeRun.id}`)
  await page.getByText('In progress', { exact: true }).waitFor()
  await page.screenshot({ path: shot(`${info.project.name}-light-run-active.png`), fullPage: true })
})

test('run: untested pull request', async ({ page }, info) => {
  await signIn(page)
  await mockApi(page)
  await page.goto(`/app/runs/${untestedRun.id}`)
  await page.getByText('Pull request open, untested').waitFor()
  await page.screenshot({ path: shot(`${info.project.name}-light-run-untested.png`), fullPage: true })
})

test('run: tests failed', async ({ page }, info) => {
  await signIn(page)
  await mockApi(page)
  await page.goto(`/app/runs/${failedRun.id}`)
  await page.getByText('no pull request was opened').first().waitFor()
  await page.screenshot({ path: shot(`${info.project.name}-light-run-failed.png`), fullPage: true })
})

test('new project and settings', async ({ page }, info) => {
  await signIn(page)
  await mockApi(page)
  await page.goto('/app/projects/new')
  await page.getByLabel('Repository').waitFor()
  await page.screenshot({ path: shot(`${info.project.name}-light-new-project.png`) })
  await page.goto('/app/settings')
  await page.getByText('Signed in to GitHub as').waitFor()
  await page.screenshot({ path: shot(`${info.project.name}-light-settings.png`) })
})

test('empty state and not found', async ({ page }, info) => {
  await signIn(page)
  await mockApi(page, { emptyProjects: true })
  await page.goto('/app')
  await page.getByText('No projects yet').waitFor()
  await page.screenshot({ path: shot(`${info.project.name}-light-empty.png`) })
  await page.goto('/app/runs/does-not-exist')
  await page.getByText('This run does not exist.').waitFor()
  await page.screenshot({ path: shot(`${info.project.name}-light-not-found.png`) })
})

import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { Run } from '../lib/types'
import { TextField } from './Field'
import { StatusPill } from './StatusPill'
import { StepTracker } from './StepTracker'
import { Tabs } from './Tabs'

function run(overrides: Partial<Run>): Run {
  return {
    id: 'r1',
    projectId: 5,
    task: 'Add validation',
    status: 'QUEUED',
    active: true,
    analysis: null,
    plan: null,
    result: null,
    baseBranch: null,
    baseSha: null,
    branchName: null,
    pullRequestNumber: null,
    pullRequestUrl: null,
    errorMessage: null,
    sandboxSeconds: null,
    estimatedCostUsd: null,
    createdAt: '2026-10-11T12:00:00',
    updatedAt: '2026-10-11T12:00:00',
    finishedAt: null,
    ...overrides,
  }
}

const plan = { taskUnderstanding: '', filesToModify: [], filesToCreate: [], steps: [] }

describe('StepTracker', () => {
  it('marks the step in progress and the ones before it as done', () => {
    render(<StepTracker run={run({ status: 'PLANNING' })} />)

    const steps = screen.getAllByRole('listitem')
    expect(steps[0]).toHaveTextContent('(done)')
    expect(steps[1]).toHaveTextContent('(done)')
    expect(steps[2]).toHaveAttribute('aria-current', 'step')
    expect(steps[2]).toHaveTextContent('(in progress)')
    expect(steps[3]).toHaveTextContent('(not started)')
  })

  it('shows every step done for a completed run', () => {
    render(<StepTracker run={run({ status: 'COMPLETED', active: false, plan })} />)

    for (const step of screen.getAllByRole('listitem')) {
      expect(step).toHaveTextContent('(done)')
    }
  })

  it('shows where a failed run stopped', () => {
    render(<StepTracker run={run({ status: 'TESTS_FAILED', active: false, plan })} />)

    const steps = screen.getAllByRole('listitem')
    expect(steps[3]).toHaveTextContent('Write and test')
    expect(steps[3]).toHaveTextContent('(stopped here)')
    expect(steps[4]).toHaveTextContent('(not started)')
    // A stopped step is not "current": nothing is running any more.
    expect(steps[3]).not.toHaveAttribute('aria-current')
  })
})

describe('StatusPill', () => {
  it('says what the status is in words, not only in colour', () => {
    render(<StatusPill status="SANDBOX_UNAVAILABLE" />)

    expect(screen.getByText('Not tested')).toBeInTheDocument()
  })
})

describe('Tabs', () => {
  function Example() {
    const [selected, setSelected] = useState('one')
    return (
      <Tabs
        label="Example"
        selected={selected}
        onSelect={setSelected}
        tabs={[
          { id: 'one', label: 'One' },
          { id: 'two', label: 'Two', count: 3 },
          { id: 'three', label: 'Three' },
        ]}
      >
        Panel {selected}
      </Tabs>
    )
  }

  it('links the selected tab to its panel', () => {
    render(<Example />)

    const tab = screen.getByRole('tab', { name: 'One' })
    const panel = screen.getByRole('tabpanel')
    expect(tab).toHaveAttribute('aria-selected', 'true')
    expect(panel).toHaveAttribute('aria-labelledby', tab.id)
    expect(panel).toHaveTextContent('Panel one')
  })

  it('is one tab stop, moved through with the arrow keys', async () => {
    const user = userEvent.setup()
    render(<Example />)

    await user.tab()
    expect(screen.getByRole('tab', { name: 'One' })).toHaveFocus()

    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: /Two/ })).toHaveFocus()
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Panel two')

    // Wraps around at both ends.
    await user.keyboard('{ArrowRight}{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'One' })).toHaveFocus()
    await user.keyboard('{ArrowLeft}')
    expect(screen.getByRole('tab', { name: 'Three' })).toHaveFocus()

    await user.keyboard('{Home}')
    expect(screen.getByRole('tab', { name: 'One' })).toHaveFocus()
  })

  it('shows a count next to a tab label', () => {
    render(<Example />)

    expect(within(screen.getByRole('tab', { name: /Two/ })).getByText('3')).toBeInTheDocument()
  })
})

describe('TextField', () => {
  it('connects the label, hint and error to the input', () => {
    const { rerender } = render(<TextField label="Password" hint="At least 8 characters." />)

    const input = screen.getByLabelText('Password')
    expect(input).toHaveAccessibleDescription('At least 8 characters.')
    expect(input).not.toHaveAttribute('aria-invalid')

    rerender(<TextField label="Password" hint="At least 8 characters." error="Too short." />)

    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Too short.')
    expect(screen.getByRole('alert')).toHaveTextContent('Too short.')
  })
})

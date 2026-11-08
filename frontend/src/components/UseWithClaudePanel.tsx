import { Check, Copy, KeyRound, ShieldAlert, Sparkles, Terminal } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import toast from 'react-hot-toast'
import { useAuth } from '../auth/context'
import { CLAUDE_CODE_COMMAND, CLAUDE_DESKTOP_CONFIG_JSON, MCP_RUN_COMMAND } from '../config/mcp'
import { cn } from '../lib/utils'

async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

/** Returns [copied, copy]: `copied` flips to true for ~2 s after a successful copy. */
function useCopy(): [boolean, (text: string, successMsg: string) => Promise<void>] {
  const [copied, setCopied] = useState(false)
  const timer = useRef<number>()
  useEffect(() => () => window.clearTimeout(timer.current), [])
  const copy = async (text: string, successMsg: string) => {
    if (await copyText(text)) {
      setCopied(true)
      window.clearTimeout(timer.current)
      timer.current = window.setTimeout(() => setCopied(false), 2000)
      toast.success(successMsg, { id: 'mcp-copy' })
    } else {
      toast.error("Couldn't copy to the clipboard", { id: 'mcp-copy' })
    }
  }
  return [copied, copy]
}

function CodeBlock({ id, label, hint, code }: { id: string; label: string; hint?: ReactNode; code: string }) {
  const [copied, copy] = useCopy()
  return (
    <div>
      <div className="mb-1.5 flex items-end justify-between gap-2">
        <div>
          <h3 id={`${id}-label`} className="text-sm font-semibold">
            {label}
          </h3>
          {hint && <p className="text-xs text-muted">{hint}</p>}
        </div>
        <button
          type="button"
          className="btn btn-ghost btn-sm shrink-0"
          onClick={() => void copy(code, `${label} copied`)}
          aria-label={`Copy ${label}`}
        >
          {copied ? <Check className="h-3.5 w-3.5 text-amber" aria-hidden /> : <Copy className="h-3.5 w-3.5" aria-hidden />}
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      <pre
        aria-labelledby={`${id}-label`}
        tabIndex={0}
        className="overflow-x-auto rounded-xl border border-line bg-bg p-3 font-mono text-xs leading-relaxed text-ink/90"
      >
        <code>{code}</code>
      </pre>
    </div>
  )
}

/** "Use CineCircle with Claude" — MCP setup panel on /settings (FEATURES-V2.md, Feature 2). */
export function UseWithClaudePanel() {
  const { token } = useAuth()
  const [copied, copy] = useCopy()

  return (
    <section className="card space-y-5 p-4 sm:p-5" aria-labelledby="claude-heading">
      <div>
        <h2 id="claude-heading" className="flex items-center gap-2 text-lg font-bold">
          <Sparkles className="h-5 w-5 text-amber" aria-hidden /> Use CineCircle with Claude
        </h2>
        <p className="mt-1 text-sm text-muted">
          Let Claude search movies, read reviews and post as you. Our MCP server plugs CineCircle into Claude Code
          or Claude Desktop. Without a token Claude can only read public stuff; with yours it sees what you see and
          can post, upvote and comment as you.
        </p>
      </div>

      {/* token */}
      <div className="rounded-xl border border-amber/40 bg-amber-muted/30 p-3 sm:p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex gap-3">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber" aria-hidden />
            <div>
              <div className="text-sm font-semibold">Your access token</div>
              <p id="token-warning" className="text-xs text-ink/80">
                Treat this like a password. It expires in 7 days.
              </p>
            </div>
          </div>
          <button
            type="button"
            className={cn('btn shrink-0', copied ? 'btn-soft' : 'btn-primary')}
            onClick={() => token && void copy(token, 'Token copied. Keep it secret.')}
            disabled={!token}
            aria-describedby="token-warning"
          >
            {copied ? <Check className="h-4 w-4" aria-hidden /> : <KeyRound className="h-4 w-4" aria-hidden />}
            {copied ? 'Copied' : 'Copy my access token'}
          </button>
        </div>
      </div>

      <CodeBlock
        id="mcp-run"
        label="1. Start the MCP server"
        hint="From the project root, with the backend running on 8080."
        code={MCP_RUN_COMMAND}
      />
      <CodeBlock
        id="mcp-claude-code"
        label="2a. Claude Code"
        hint={
          <>
            <Terminal className="mr-1 inline h-3 w-3" aria-hidden />
            Run once in a terminal. To post as you, start the server with <code className="text-ink">CINECIRCLE_TOKEN</code> set to
            your token.
          </>
        }
        code={CLAUDE_CODE_COMMAND}
      />
      <CodeBlock
        id="mcp-claude-desktop"
        label="2b. Claude Desktop"
        hint={
          <>
            Add to <code className="text-ink">claude_desktop_config.json</code>, fix the jar path, paste your token, then
            restart Claude Desktop. Build the jar with <code className="text-ink">mvn package</code> in{' '}
            <code className="text-ink">mcp-server/</code>.
          </>
        }
        code={CLAUDE_DESKTOP_CONFIG_JSON}
      />
    </section>
  )
}

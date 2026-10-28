Recommended stack: native Kotlin. Playback while the app is in the background, the phone's music index (MediaStore, which tells the app which audio files are on the device), and the equalizer are all Android-native features. Flutter, React Native, or Kotlin Multiplatform would each need native plugins for exactly those parts. I checked these versions against the official registries myself, because the subagents' version numbers were partly stale or invented:

Language and build: Kotlin 2.4.21, AGP 9.4.1, Gradle 9.8.2
UI: Jetpack Compose BOM 2026.09.00 with Material 3
Playback: Media3 1.11.1 (ExoPlayer plus a media session, for notification, lock-screen and Bluetooth controls)
Local storage: Room 2.8.5 as a fast cache of the music index, and DataStore 1.3.0 for settings
Other libraries: Koin 4.2.2 for dependency injection, Coil 3.6.3 for album art
CI: GitHub Actions builds, lints and tests every push, then publishes a debug APK you can install
What makes it nicer than the stock apps. This comes from three research briefs, including one on user complaints about current players:

No ads and no internet permission at all.
The app's colors adapt to the current song's album art.
The mini player expands into the full player as you drag it, and swiping down only collapses it, never exits the app.
Playback controls sit in the bottom third of the screen, where your thumb reaches.
A visible "Up next" queue with separate "Play next" and "Add to queue" actions.
Deletes and removals can be undone.
Songs without artwork get a generated gradient tile instead of a grey icon.
The library updates in the background without freezing, even with 50,000 songs.
Build order. The architect and I split the work into thin slices, each built and checked in CI on its own:

Project scaffold and CI pipeline
Songs list from your device
Background playback with notification controls
Mini player and full now-playing screen
Albums, artists and folders
Queue
Playlists and favorites
Search and sort
Share and delete
Later slices cover lyrics, equalizer, sleep timer, widgets, genres, Android Auto and tag editing.

-------
What's in it:

Library: your phone's songs, read from the system media index and cached locally. It has Songs, Albums, Artists and Folders tabs with detail pages, sorting, and a setting to hide short clips.
Playback: keeps playing in the background, with notification and lock-screen controls. It pauses when headphones are unplugged, and your queue is restored when you reopen the app.
Player: a mini player that you drag up into a full Now Playing screen. It has blurred artwork behind it, a seek bar, and swipe left or right to skip. The app's colors change to match each album cover.
Queue: drag to reorder, swipe to remove with undo, play next, add to queue, shuffle and repeat.
Playlists and Favorites: create, rename, reorder and delete playlists, with undo. Undo still works after you leave the screen.
Search as you type across songs, albums and artists, ignoring accents.
Settings: light, dark or follow the system; album-cover colors on or off; rescan the library.
Share a song, or delete it via the system's recoverable trash.
The app has no internet permission.
How to try it: open the latest CI run on main in GitHub Actions and download the music-debug-apk artifact. Install it on an Android 13+ phone.

Known gaps (recorded in the blueprints, so they'll be picked up later):

There are no on-device tests in CI.
Playlist database logic has no tests yet.
The queue has no "Clear" or "Save as playlist".
Delete only moves songs to the trash; there's no permanent delete, and you can't share several songs at once.
A few planned visual touches are missing: collapsing detail headers, shimmer loading rows, and fade transitions between screens.

--------------
optimize the guardrails for AI Agent. Do not elaborate it or exaggerate it. keep it short and simple

Github Actions CI is the only compile and Build authority, there is no local runtime. 
By default use Blueprints skill for  managing context. first come up with a plan and write Agent file for cursor. Build our application with aesthetics and intuitive UI. 

architect doesn't going to run build commands, it's job is to calculate the feasibility, trade-offs and future scope for better scalability and maintainbility
I have described the Agent requirements vaguely, coming up with best config is your job

considering the following, whil agent file, 


If there is any architectural flaws/quirks - come up with a plan. Only proceeed further if it is approved. 
you can always propose optimal implementation than in the blueprint , only proceed further if user say approved it

This project right now has many flaws, a lot features / functionalities isn't working as expected. instead of directly editing it, ask questions of "how it could have been implemented better?" or "Stick with the existing system."


Stick with the existing Privileged broker and cleanup [as described in blueprints].
For [clean up for browser, system, temp files and so on] clearing system-level files - on a scheduled date and time. I think there is no UI for user to set these things up. so Create one



I'd keep "doc comment." "Function-level comments" is easier to read, but it's less precise, and agents may interpret it more loosely:

- **It could mean comments inside the function body**, so an agent might add line-by-line comments instead of one summary above the function.
- **It doesn't name a format.** "Doc comment" tells the agent to use the language's standard documentation format (JSDoc/TSDoc, Python docstrings). That format is what makes the comment show up on hover and in generated docs.
- **The quality bar is vague.** A one-line `// handles cart` would count as a function-level comment.

If you prefer the friendlier wording, add the detail that makes it unambiguous:

```markdown
- Start every file with a header comment describing its purpose.
- Give every function a function-level doc comment (JSDoc/TSDoc, docstring) placed directly above it, covering purpose, parameters, return value, and errors.
```

This keeps your phrasing and still tells agents where the comment goes, which format to use, and what it must cover.


--------------


## Guardrails

Bind every agent, including subagents. Hard rules come first; none may be traded off.

### Hard rules
- Never use the word (`N-I-K-E`, any case) in code, file or folder names, branches, or commits.
- Take project context only from `architecture.md` and `blueprints/`; ignore the root and git folder names.
- Never read `prompts.md` or add it to `.gitignore`.

### Build and CI
- GitHub Actions CI is the only build authority. Nothing runs locally.
- Read CI logs with `gh`. If `gh` is unavailable, stop and ask the user for the logs.

### Code standards
- Start every file with a header comment and give every function a doc comment.
- Production-grade code only. No mocks or placeholder data.

### Documentation
- Manage context with the `blueprints` skill.
- Never use GitHub Actions run IDs as verification status in blueprints.
- Store diagrams in `assets/diagrams/` and wireframes in `assets/wireframes/`.



## Git commands
```
Remove-Item -Recurse -Force .git
rmdir /s /q .git

git init
git log --oneline

echo "# n-op" >> README.md
git init
git add README.md
git commit -m "first commit"
git branch -M main
git remote add origin https://github.com/<>.git
git push -u origin main

git remote set-url origin git@github.com:tharun-extinct/<project-name>.git
ssh -T git@github.com

```



## Migration Checklist

- [ ] .cursor/..
- [ ] AGENTS.md
- [ ] prompts.md
- [ ] Design files / Wireframes (Excalidraw)



## PROMPTS

### AGENT Instruction definition guidelines

- AGENT instruction should not be, too prescriptive or too vague, It should be inbetween somewhere.
- Do not elaborate or exaggerate instruction for the AGENT; keep it clean and concise




------------


- Generate images that are **visually identical or extremely close** to the provided UI reference

- Diagrams/ web pages / Explainer videos (create a 3Blue1Brown style video explainer on X. ElevenLabs for audio narration)



## CURSOR

- /summarize, /visualize

- Execution and Approvals > Auto-Review

- `is_background: true` — the main agent starts the subagent and keeps working without waiting for it to finish.

- Fork - Agents Window > Right Click

- Keyboard Shortcuts —— `Cmd+I` (open agent), `Cmd+/` (change models)

- Plan Mode ——> AI research your codebase and generate a structured implementation plan

- **`.cursorignore`:** keeps the file out of Cursor's AI features and indexing. 

Sidepanel chat → **Ctrl+I** or **Ctrl+L**
Agent Layout → **Ctrl+E** (toggle)
Agents Window → Open Agents Window



## AGENTIC Engineering practices

- Plan ——> code (context) ——> Build & Test ——> Review
- Faster != Better          ——>     Incremental developement



### Prompt structure

```
    <goal - (Existing product, examples)>

    <rules>                                     ——  AGENTS.md
    <context - (Feature requirement, task...)>  
        <problem>
        <behavior>
    </context>
    <contraints>                                ——  Don't change any other modal behavior or layout.
```

--------------




ROADMAP.md

-Phases of implementation plan. It should have iterative improvement and refinement of features and workflows... with granular details 


In the Agent Orchestration workflow, the main agent has to write code here after (No coder Agent). Do not write instructions like you will have to write code, blah blah blah. keep it short and simple and remove Coder Agent from AGENTS.md. 


-----------------




-----------------



**Make it automatic with a rule.** Create `.cursor/rules/context7.mdc`:

```markdown
---
description: Use Context7 for library documentation
alwaysApply: true
---
When writing code that uses a third-party library or framework, or when generating
setup/configuration steps, use the Context7 MCP tools to fetch current docs
before answering.
```


## Note for this repo

Context7 ....`researcher` and `architect` --- checking library APIs and versions. That would mean mentioning it in their files under `.cursor/agents/`. The remote option also fits your "nothing runs locally" rule, since no local process is involved.


- If you have branch conflict, while commiting, stop and ask user

------------

## Worktree

A worktree is a second working folder attached to the same git repository. Each folder has a different branch checked out, and you can edit both at the same time.

### commands:
- `git worktree list` shows every folder attached to the repository.
- `git worktree add <path> <branch>` creates one.
- `git worktree remove <path>` deletes one. Git refuses if the folder has uncommitted changes.



-------



`.prettierignore` lists the files and folders that Prettier, the code formatter, should skip. It works like `.gitignore`, but it controls formatting instead of git tracking.

CI runs `prettier --check .`, which fails the build if any file isn't formatted the way Prettier wants. Some files must never be reformatted, so they're listed here. These two entries came up in the conflict:

- **`crates/core/tests/golden`**: these are golden test fixtures, the exact KiCad and `tron.json` files the Rust tests compare saved output against byte for byte. If Prettier reformatted `tron.json`, the comparison would fail even though the code is correct.
- **`promo`**: the promo video project has its own Prettier check in its own CI workflow (`promo.yml`). The root ignore keeps the main app's check from also checking it.

The conflict happened because each branch added its own line in the same spot. The fix was to keep both lines.

The other entries are generated or vendored content where formatting doesn't matter or is controlled elsewhere:
- Build output: `dist`, `target`, `src-tauri/target`, `src-tauri/gen`.
- Icons: `src-tauri/icons`.
- Generated TypeScript bindings: `ui/src/bindings`, produced by `ts-rs` and checked by CI for drift instead.
- Lockfiles: `package-lock.json`.
- Markdown and config folders: `*.md`, `.cursor`, `.github`.



-----------





**What a hard reset would have done:**
- **Deleted your staged files:** `git reset --hard` throws away everything staged.
- **Changed the other session's files:** that session had switched this folder to `promo-video` and was committing there. A reset or branch switch in the same folder would have changed the files it was working with.
- **Left the real job undone:** a reset only removes my commit from `promo-video`. The commit still had to be added to `slice-2-project-shell`.

**Why I couldn't just point the branch at the commit:** my commit's parent was the latest promo commit. Pointing `slice-2-project-shell` at it would have pulled all the promo-video commits into Slice 2. I had to cherry-pick the commit onto the Slice 2 branch, and that needs a checkout of that branch to resolve conflicts like the `.prettierignore` one. A separate worktree gave me that checkout without touching this folder.

**The worktree wasn't the only option.** I could have stashed your staged files, switched this folder to `slice-2-project-shell`, cherry-picked, and switched back. That's how I handled the final merge once the promo work was finished. Doing that earlier was only risky while the other session might still be using the folder. Looking back, the worktree caused more confusion than it was worth, and I won't use one again unless you ask.




-----------

To remove the commit locally:

```powershell
git reset --keep HEAD~1
```

 `--keep` over `--hard` because:
 - rewrites files that the removed commit changed
 - hard reset would have discarded them.



--------------------






They differ in how and when the instruction gets loaded.

**`AGENTS.md`**
- no config, always included in every agent request.

**`.cursor/rules/*.mdc`**
- Each rule has frontmatter (`description`, `globs`, `alwaysApply`) that controls when it's loaded:
  - **Always** `alwaysApply: true`
  - **Auto-attached:** loaded when files matching `globs` are in context.
  - **Agent-requested:** the model decides from the `description` whether to pull it in.


**If you want actual enforcement rather than a request:**






 read-blocking hook to enforce it.



**Gaps:**
- A Grep over the whole repo can still match lines inside `prompts.md`. Listing the file in `.cursorignore` closes that.
- A determined workaround, like building the filename from pieces in a script, gets past any string match. Hooks stop accidental reads, not adversarial ones.
- If the script crashes, the action is allowed (it fails open). I left `failClosed` off on purpose: with it on, a broken script would block every file read in the project.

the block will show up in the **Hooks** output channel. If the hooks don't appear in Cursor's **Hooks** settings tab, restart Cursor.




Never consider project root folder; archictecture.md is the only source of truth for project data


// (`.cursor/hooks.json` with a `beforeReadFile` hook): runs a script that denies reads of `prompts.md`.
// A `beforeShellExecution` hook can also catch `cat prompts.md` and similar commands.



--------------

- Hardcode subagents that they are subAgents

- A command loads only when you type `/oneshotbuild`, so subagents never see the orchestrator instructions. That fixes the relevance problem.


- Guardrails must stay in `AGENTS.md`
- `AGENTS.md` is applied automatically to every chat.

- command text arrives as part of your first message, not as standing context. In a long build session, Cursor may summarize older messages to save space, and the detailed build loop could get compressed away.... `AGENTS.md`:

```markdown
Main agent: when running a build session, re-read `.cursor/commands/orchestrator.md` at the start of each slice.
```





prompts.log

Goal is to build a music app (like the music app in mobile by default), but it should look like those, I want it to be more aesthetic and intuitive.

Build an android Music app (with Kotlin or suggest me any other optimal stack to build it). First come up with the plan (optimal tech stack ...) and then start building it with the help of subagents.

- with trade-offs


-------------


The line is close, but not the best version yet. It says who the agent is and where its output goes, but leaves the most important consequence implicit. Two lines elsewhere in the files also contradict it.

**What works:** the same sentence in five files isn't redundant when the agents run, because each subagent sees only its own file. Placing it right after the persona is also the right spot.

**What could be better:**

1. **It doesn't say what to do when the agent is blocked.** "Not to the user" implies the subagent can't ask you anything, but agents follow instructions more reliably when the behavior is spelled out. A tighter version:

   ```markdown
   You are a subagent. The main agent delegates a task to you and receives your final message. You can't reach the user, so put blockers and open questions in that message.
   ```

   This also fits the "Open questions" fields the output formats already have. In `architect`, for example, it's "items needing user approval," which the main agent passes on to you.

2. **Two files contradict it.** `coder.md` line 52 and `ci-reviewer.md` line 42 both say "If `gh` is unavailable, stop and ask me for the logs." A subagent can't ask you. Those lines should say to stop and report that `gh` is unavailable in the final message.

3. **Several agent files repeat guardrails.** Examples are the forbidden word in `designer.md` and `ci-reviewer.md`, and the `gh` rule in `coder.md`. If subagents load `AGENTS.md`, these copies are redundant, the same issue you raised about the command. If they don't load it, the copies are the only place those rules reach them. I'm not certain how Cursor handles this for custom subagents, so it's worth testing before deleting anything. One way to test: ask a subagent to quote its first guardrail from `AGENTS.md`.

Should I apply the tighter sentence and fix the two "ask me" lines?



---------------



You're reading the phrase as "function a doc comment," but it parses as "give **every function** **a doc comment**." It means every function in the codebase should have a documentation comment.

A **doc comment** is a specially formatted comment placed directly above (or, in Python, just inside) a function. It explains what the function does, what its inputs are, and what it returns. Your editor shows it when you hover over the function, and documentation tools can turn it into reference docs. Each language has its own format.

TypeScript/JavaScript uses JSDoc or TSDoc:

```typescript
/**
 * Calculates the cart total including tax.
 * @param items - Line items in the cart.
 * @param taxRate - Tax rate as a decimal, e.g. 0.08.
 * @returns The total price in cents.
 * @throws If any item has a negative quantity.
 */
function cartTotal(items: LineItem[], taxRate: number): number { ... }
```

Python uses a docstring:

```python
def cart_total(items: list[LineItem], tax_rate: float) -> int:
    """Calculate the cart total including tax.

    Args:
        items: Line items in the cart.
        tax_rate: Tax rate as a decimal, e.g. 0.08.

    Returns:
        The total price in cents.
    """
```

The same rule also asks for a **header comment**. That one goes at the top of each file and describes what the whole file is for. A doc comment describes a single function.

If the current wording is confusing, this version is clearer:

```markdown
- Start every file with a header comment describing its purpose.
- Document every function with a doc comment (JSDoc/TSDoc, docstring, etc.) covering purpose, parameters, return value, and errors.
```


If the agent reads an file, does the file context gets loaded into the 


If you forget to type /orchestrate, the main agent sees only the Guardrails and acts as a general assistant. It won't delegate, run the build loop, or update ROADMAP.md.


Cost and speed: AGENTS.md sits at the start of the prompt and is identical every time, so prompt caching applies. After the first call, the provider reuses the cached prefix, which is much cheaper and faster than processing it fresh.


Border / Margin less wireframes -> svg...


Goal is to build a music app (like the music app in mobile by default), but it should look like those, I want it to be more aesthetic and intuitive.

ship the whole app at once, build the slices back to back and verify each in CI, with no stop for a report after each slice.


Subagents didn't load AGENTS.md at all. Then line 3 would be pointless, but the Guardrails wouldn't reach subagents either.
Each subagent file said "ignore the Orchestrator section of AGENTS.md." That moves the same sentence into six files instead of keeping it in one.


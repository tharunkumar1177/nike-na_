REA is set up once per machine, not per workspace. One command adds it to Cursor, and every workspace, including this one, can then use it. It can't be installed yet because Node.js isn't on this machine (`node` and `npm` aren't recognized in PowerShell), so that comes first.

REA Reverse Engineer Anything, 
- npm package [`rea-agents`](https://www.npmjs.com/package/rea-agents) is a local MCP server and CLI. It gives an agent tools to inspect apps you don't have the source for, such as native binaries, JavaScript and Electron apps, .NET assemblies, websites and Android APKs. 
- installs a `reverse-engineer-anything` skill that tells the agent how to run an investigation.

## Setup steps

1. **Install a supported Node.js version.** REA 6.8 needs Node 22.x (22.23.4 or later) or 24.x (24.22.1 or later). Versions 23 and 25 aren't supported. Afterwards, restart PowerShell and Cursor so they pick up the new `PATH`.

   ```powershell
   winget install OpenJS.NodeJS.LTS
   ```

2. **Preview the setup, then run it.** The REA docs say to run it from a trusted directory outside your project:

   ```powershell
   cd $env:USERPROFILE
   npx rea-agents setup --client cursor --dry-run
   npx rea-agents setup --client cursor
   ```

   Setup lists the files it will change and waits for your approval. It adds an `mcpServers.rea` entry to `~/.cursor/mcp.json` and installs the workflow skill. You already have a `~/.cursor/mcp.json`; setup keeps your other servers and makes a backup first.

3. **Restart Cursor and check it works:**

   ```powershell
   npx rea-agents doctor --client cursor --json
   ```

   Then look under Cursor Settings → MCP and confirm `rea` shows as connected. If `doctor` passes but Cursor can't start REA, Cursor is probably using a different `PATH`; check the MCP error output.

4. **Add an analysis engine only if you need one:**
   - **Websites and JavaScript/Electron apps:** nothing extra.
   - **Android APKs:** JADX and Apktool. JADX is set through `REA_JADX_MCP_JAR`.
   - **Native Windows binaries:** Ghidra 12.1 or later with a full 64-bit JDK. Windows support is experimental and read-only. Hopper can't be installed on Windows.

## How this fits your project

- **Which agent uses it.** Your `.cursor/orchestrator.md` sends all reference-product research to the `researcher` subagent, so that's where REA belongs. Right now `.cursor/agents/researcher.md` doesn't mention it. Adding a line like "when the reference product's app, APK or website is available locally, investigate it with REA's MCP tools and cite their evidence" would route that work through REA. One thing to test after setup: I haven't confirmed that a read-only subagent can call MCP tools. If it can't, the main agent would have to make the REA calls.
- **Your "nothing runs locally" rule.** `AGENTS.md` says nothing runs locally. That rule is written about builds and CI, but REA's analysis runs entirely on your machine. You should decide whether the rule covers it.
- **Keeping it to this workspace only.** Cursor also reads a project-level `.cursor/mcp.json`, but REA's setup only writes the global file. A project-only entry would have to be written by hand, and its exact format for Cursor isn't something I've checked yet.

Once Node is installed and setup is done, I can add the REA line to `researcher.md`.




---


The only difference is that `--dry-run` writes nothing. It prints the changes setup would make and exits; you read that plan, then run the second command, approve the same changes, and setup writes them.

REA's setup has no per-project option for Cursor. It always writes to your user-level `~/.cursor/mcp.json`, which every workspace loads. If you want REA only in this workspace, don't let setup register it. Write the project config by hand instead.

## What each command does

| Command | Writes files? | What you get |
| --- | --- | --- |
| `setup --client cursor --dry-run` | No | A plan showing which files would change, the exact Node path and REA script path it would register, and whether the skill would be installed. Exits with status `planned`. |
| `setup --client cursor` | Yes, after you approve | The `rea` entry added to `~/.cursor/mcp.json` (with a backup), plus the workflow skill installed. |

The dry run is also the easiest way to find the absolute Node and REA paths you need for a hand-written project config.

## Where project-specific config comes in

Cursor reads MCP servers from two places:

- **`~/.cursor/mcp.json`**: user-level, loaded in every workspace. This is the only file REA setup writes.
- **`c:\nike-naa\.cursor\mcp.json`**: project-level, loaded only when this folder is open. This file doesn't exist yet, and REA never creates it.

To limit REA to this workspace:

1. **Install REA globally** so the paths don't depend on npx's temporary cache. REA's docs recommend this as the more durable option. Run it from outside the repo:

   ```powershell
   cd $env:USERPROFILE
   npm install -g rea-agents
   rea setup --client cursor --dry-run
   ```

   Don't apply the plan. Just copy the Node path and the `rea.mjs` path it shows.

2. **Create `c:\nike-naa\.cursor\mcp.json`** using REA's documented manual format, with those paths filled in:

   ```json
   {
     "mcpServers": {
       "rea": {
         "command": "C:\\Program Files\\nodejs\\node.exe",
         "args": ["C:\\path\\from\\dry-run\\rea\\scripts\\rea.mjs", "mcp"]
       }
     }
   }
   ```

   You can also add an `"env"` block here for settings that apply only to this project, such as `REA_JADX_MCP_JAR` for APK analysis or the Ghidra timeouts.

3. **Add the skill at project level.** Copy the `.agents/skills/reverse-engineer-anything` folder from the [REA repo](https://github.com/morluto/rea) into `c:\nike-naa\.cursor\skills\reverse-engineer-anything\`. It will sit next to your existing `blueprints` skill. Setup would normally install it globally.

4. **Restart Cursor** and check that `rea` appears under Settings → MCP while this workspace is open.

## Tradeoffs of the project-only route

- **REA's maintenance commands may not see it.** For Claude Code's equivalent per-project setup, the docs say `rea doctor`, `rea update` and `rea uninstall` read only the user-level entry. Expect the same for a hand-written Cursor entry, so you'd update its paths yourself after upgrading REA.
- **The file is easy to commit by accident.** `.cursor/mcp.json` would hold absolute paths specific to your machine. Add it to `.gitignore` if you don't want it in the repo. REA's docs warn about this for the same reason.
- **No backup is made for you.** Setup backs up files before changing them, but nothing does that when you edit the file by hand.

If you'd rather have REA available everywhere, apply the second command and skip the manual steps. Once Node is installed and you've run the dry run, I can write the project-level `mcp.json` and copy in the skill.


---


No. Those two commands install REA's code on your machine but don't connect it to Cursor, so the agent can't use it yet. `npm install -g rea-agents` puts the `rea` command and the MCP server code on disk. `rea setup --client cursor --dry-run` only prints a plan and writes nothing.

## What actually connects REA to Cursor

After installing, you need one of these:

- **For every workspace:** run `rea setup --client cursor` (without `--dry-run`) and approve the plan. This adds REA to `~/.cursor/mcp.json` and installs the workflow skill.
- **For this workspace only:** write `c:\nike-naa\.cursor\mcp.json` yourself, using the Node and `rea.mjs` paths the dry run printed, and copy the skill into `.cursor/skills/`.

Restart Cursor afterwards in either case.

## Does the agent use REA through MCP tool calls?

Yes. Once REA is registered, Cursor starts it in the background (`node rea.mjs mcp`) and the agent calls its tools the same way it calls any other MCP tool. Three pieces work together:

- **MCP tools** do the analysis. The agent calls them, for example to open a target, decompile a function or trace a feature.
- **The `reverse-engineer-anything` skill** tells the agent how to run an investigation: open the target, then trace what it does, keep evidence for each step, and finally rebuild the feature in your stack.
- **MCP prompts**: REA also provides 6 guided workflows, such as `investigate_feature`, which point the agent to the right tools for a given type of target.

The agent can also run the `rea` command from a terminal (for example `rea analyze <path>`), but the MCP tools are the main route.

## The tools

REA lists about 213 tools by default. Below are the ones named in REA's documentation and pull requests, grouped by area. I collected these names from the docs, so treat this as indicative rather than exhaustive. For the exact list, look at the server under Cursor Settings → MCP once it's installed, or have the agent call `binary_session` with `{}`. That shows every tool and whether it can run for the current target.

- **Session and guidance:** `open_binary`, `close_binary`, `binary_session`, `reset_investigation`, `discover_tools`, `search_guidance`, `read_guidance_section`
- **Native binaries** (these need Ghidra, Hopper, IDA or Kuna): `binary_overview`, `list_procedures`, `search_procedures`, `list_strings`, `search_strings`, `list_names`, `list_segments`, `analyze_function`, `batch_decompile`, `decompile_all`, `procedure_pseudo_code`, `procedure_callers`, `procedure_callees`, `procedure_references`, `get_call_graph`, `build_call_path`, `trace_call_path`, `resolve_containing_procedure`, `inspect_address_context`, `inspect_native_instruction`, `inspect_native_load_image`, `address_to_file_offset`, `read_bytes`, `search_bytes`, `trace_native_values`, `trace_native_ui_action`, `match_native_functions`, `compare_functions`, `demangle_swift`, `inspect_macho`, `inspect_analysis_settings`, `update_analysis_settings`
- **JavaScript, Electron and web:** `analyze_javascript_application`, `analyze_web_bundle`, `trace_javascript_semantics`, `reconcile_javascript_runtime`, `compare_javascript_export_shapes`, `compare_source_to_bundle`, `inspect_electron_fuses`, `inspect_web_page`, `capture_browser_scenario`, `compare_web_captures`, `discover_webmcp_tools`
- **Android and mobile:** `project_android_application_graph`, `inspect_apk_signature`, `inspect_apktool_client`, `inspect_adb_client`, `inspect_dart_aot` (Flutter), `project_apple_application_graph`, `project_harmony_application_graph`
- **.NET (managed code):** `project_managed_application_graph`, `compare_managed_members`, `verify_managed_native_boundaries`, `import_managed_reconstruction`
- **Packages and resource files:** `inspect_artifact`, `inventory_artifact`, `extract_artifact`, `compare_artifacts`, `inspect_binary_layout`, `inspect_wasm_artifact`, `compare_wasm_interfaces`, `inspect_erlang_beam`, `inspect_python_pyc`, `open_bundle_resources`, `inspect_plist`, `decode_interface_builder`, `inspect_asset_catalog`, `inspect_keyed_archive`
- **Feature tracing and version comparison:** `trace_feature`, `trace_application_feature`, `find_changed_behavior`, `compare_application_versions`, `correlate_static_and_runtime`, `capture_process_scenario`, `compare_process_captures`
- **Evidence and reconstruction:** `get_evidence_bundle`, `export_evidence_bundle`, `export_environment_bundle`, `inspect_analysis_view`, `get_navigation_context`, `inspect_input_contract`, `record_unknown`, `update_unknown`, `list_unknowns`, `build_reconstruction_obligation_ledger`, `evaluate_reconstruction_coverage`

## Limiting the tool list

Every tool's description takes up room in the agent's context, so 200+ tools is a lot to carry. You can limit REA to specific families with the `REA_MCP_TOOL_FAMILIES` setting. Put it in the `env` block of the `mcp.json` entry, or pass `rea setup --tool-families ...`. The families named in the docs include:

- `session`: the base set, which almost every workflow needs
- `native`, `enhanced`, `direct`
- `browser`, `web`, `electron`
- `android`, `managed`, `firmware`, `artifact`
- `evidence-only`

This project's modules (`engine/media3`, Kotlin) suggest an Android app, so `session,android,web` would be a reasonable starting set if your reference product is an Android app or a website. For comparison, REA's own tests show `session` alone exposes about 23 tools, and `session,native` about 46.



---


Yes. JADX is the decompiler REA uses to turn an APK's DEX bytecode into Java, so if you already have the decompiled code, REA doesn't need it.

There's a further point: once you have readable source, REA adds little for that part. REA's own skill description says "skip REA for ordinary source-repository architecture analysis." The agent can just read the decompiled files with its normal search and read tools.

REA is still useful for the parts of an APK that decompiled Java doesn't cover:

- **Native libraries (`.so` files):** media and video apps often put codecs, filters and rendering in native code reached through JNI. JADX doesn't decompile these. They need a native engine such as Ghidra, plus REA's native tools like `analyze_function` and `trace_native_values`.
- **Resources and the manifest:** if your decompiled output doesn't include decoded `AndroidManifest.xml`, layouts and string tables, REA uses Apktool for those. JADX output usually includes them, so check yours.
- **Flutter apps:** the Dart code is compiled ahead of time into native code, so JADX output would show almost nothing. REA's `inspect_dart_aot` reads it without JADX.
- **APK signatures:** `inspect_apk_signature` works on the APK directly and needs no extra tools.
- **Runtime behavior:** watching the app run on a device or emulator uses adb (`inspect_adb_client`), not JADX.

Two practical points if you hand over decompiled code:

- **Keep it outside this repo** and give the agent absolute paths. This workspace's rules say project context comes only from `architecture.md` and `blueprints/`, and decompiled third-party code shouldn't end up committed.
- **Have the agent describe behavior, not copy code.** REA's intended workflow is to work out how a feature behaves, then rebuild it in your own stack (a "clean-room" rebuild). That works the same whether the agent reads REA's output or your decompiled files.



---


Right, not even the JADX command-line tool. REA doesn't call the `jadx` CLI anyway. Its Android tools use a headless JADX server that you point to through `REA_JADX_MCP_JAR`, and that server needs a full JDK. If you supply the decompiled code yourself, you can skip all of it: the CLI, that jar and the JDK.

You'd only need JADX again in these cases:

- **You want REA to analyze the original APK.** Tools like `project_android_application_graph` and REA's method decompilation read the APK through JADX, and they record evidence tied to the APK file itself.
- **Your decompiled output has gaps.** If some classes failed to decompile or are heavily obfuscated, you'd need to re-run JADX on those parts, with different settings or a newer version.
- **You're comparing app versions.** Comparing two releases through REA works on the APKs, so it needs JADX for each one.

If none of those apply, the agent can work from your decompiled files with its normal tools, and you'd use REA only for the parts JADX doesn't cover, such as native `.so` libraries.


---


## Removing REA from Cursor

How you remove it depends on how you added it.

**If you ran `rea setup`**, use REA's own uninstall, then remove the package:

```powershell
rea uninstall
npm uninstall -g rea-agents
```

Then restart Cursor.

- `rea uninstall` removes only REA's own agent registrations and skill files. It keeps your other MCP servers, other skills, Node.js and any evidence files or captures.
- It applies to every agent REA configured, not just Cursor. If you also set it up for something like Claude Code, that gets removed too. Run `rea uninstall --help` to see whether there's an option to limit it to one client.
- Add `--purge-data` if you also want REA's cache and state deleted, for example `~/.rea`.

**If you wrote the project-level config by hand**, `rea uninstall` won't find it, because REA's maintenance commands only read user-level registrations. Remove these yourself:

- the `"rea"` entry in `c:\nike-naa\.cursor\mcp.json` (or delete the file if REA is the only entry)
- the `c:\nike-naa\.cursor\skills\reverse-engineer-anything\` folder

Then restart Cursor.

## Where to get the REA skill

There are three sources:

1. **The npm package itself.** `rea setup --client cursor` installs the skill into Cursor's personal (user-level) skills folder. This copy matches your installed REA version, so it's the safest choice.
2. **skills.sh**, to install only the skill:

   ```powershell
   npx skills add morluto/rea --skill reverse-engineer-anything
   ```

   A third-party listing adds `-a cursor -y` to target Cursor directly. This route installs only the instructions; it doesn't register the MCP server.
3. **The GitHub repo**: the [`.agents/skills/reverse-engineer-anything`](https://github.com/morluto/rea/tree/main/.agents/skills/reverse-engineer-anything) folder in `morluto/rea`. For a project-only setup, copy that folder into `c:\nike-naa\.cursor\skills\`.

**Watch out for version mismatches.** The skills.sh and GitHub copies follow the repo's latest code, which can be ahead of your installed npm release. They may tell the agent to call tools your installed server doesn't have yet.

**The skill doesn't work on its own.** It's only instructions for running an investigation. Without the MCP server registered, the agent has no REA tools to call, though it can still read decompiled code you provide.


---



```powershell
npx skills add morluto/rea --skill reverse-engineer-anything -a cursor -y
```

- **`-a cursor`** installs the skill for Cursor.
- **`-y`** skips the confirmation prompts.

tools - `rea setup --client cursor` 

For a project-only install, run it from the repo root (`cd c:\nike-naa`). I believe the `skills` CLI installs into the current project by default and needs a `-g` flag to install globally, but I haven't checked that. Afterwards, confirm the files ended up in `c:\nike-naa\.cursor\skills\reverse-engineer-anything\` and not in your user folder.


node vs node js vs npm vs npx (how npx comes with node js)



$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User"); node --version; npm --version



npx --yes skills add morluto/rea --skill reverse-engineer-anything -a cursor -Y



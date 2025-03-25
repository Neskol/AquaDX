# AquaNet

This is the codebase for the new frontend of AquaDX. 
This project is also heavily WIP, so more details will be added later on.

## Development

This project uses Svelte (NOT SvelteKit) + TypeScript + Sass, built using Vite. The preferred editor is VSCode.

### Running locally

First, you would need to install Node.js and bun.
Then, you would need to start your testing AquaDX server and configure the `aqua_host` in `src/libs/config.ts` to use your URL. 
Please leave `data_host` unchanged if you're not sure what it is. 
Finally, run:

```shell
bun install
bun run dev
```

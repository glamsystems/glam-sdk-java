# ix-mapper-ts (synced)

The mapping documents of the GLAM instruction mapper, in the layout of the TypeScript
package `packages/glam/ix-mapper-ts` in the GLAM monorepo: the generated documents under
`src/generated/mapping/{production,staging}`.

The GLAM monorepo's public-sync workflow writes this directory, in a commit that names the
monorepo commit it came from; the same workflow publishes the package to
`glamsystems/ix-mapper-ts`. Until the first sync, the directory holds a copy made by hand
from `glamsystems/ix-mapper-ts` at 16320bf; from then on nothing here is edited by hand: a
document changes in the monorepo and arrives with the next sync.

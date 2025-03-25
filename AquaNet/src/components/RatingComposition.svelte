<!-- Svelte 4.2.11 -->

<script lang="ts">
  import RatingCompSong from "./RatingCompSong.svelte";
  import { parseComposition, type GameName } from "../libs/scoring";
  import { type MusicMeta } from "../libs/generalTypes";

  export let title: string;
  export let comp: string | undefined;
  export let allMusics: Record<string, MusicMeta>;
  export let game: GameName;
  export let top: number | undefined = undefined;

  let split = comp?.split(",")?.filter(it => it.split(":")[0] !== '0')
    ?.map(it => parseComposition(it, allMusics, game))

  if (top) split = split?.toSorted((a, b) => b.score - a.score).slice(0, top)
  if (split) console.log("Split", split)
</script>

{#if split && comp}
<div>
  <h2>{title}</h2>
  <div class="rating-composition">
    {#each split as p}
      <div>
        <RatingCompSong {p} {game}/>
      </div>
    {/each}
  </div>
</div>
{/if}

<style lang="sass">
  @use "../vars"

  .rating-composition
    display: grid
    // 3 columns
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr))
    gap: vars.$gap
</style>

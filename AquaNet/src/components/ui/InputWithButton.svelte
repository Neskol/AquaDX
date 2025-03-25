<script lang="ts">
  import { slide } from "svelte/transition";
  import { ts } from "../../libs/i18n";

  export let field: {key: string, value: string, changed?: boolean};
  export let callback: () => Promise<boolean>;
</script>

<div class="field">
  <input id={field.key} type="text" bind:value={field.value}
  on:input={() => field.changed = true}/>
  {#if field.changed}
    <button on:click={async () => { if (await callback()) field.changed = false } }
      transition:slide={{axis: 'x'}}>
      {ts('settings.profile.save')}
    </button>
  {/if}
</div>

<style lang="sass">
  .field
    display: flex
    align-items: center
    gap: 1rem
    width: 100%

    input
      flex: 1
</style>

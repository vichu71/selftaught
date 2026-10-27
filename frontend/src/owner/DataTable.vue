<script setup lang="ts">
// Tabla accesible: título, cabeceras de columna y un guion donde no hay dato. Hace scroll dentro de su caja para no desbordar la página en el móvil.
defineProps<{ columns: { key: string; label: string }[]; rows: Record<string, string | number | null>[]; caption: string }>()
</script>

<template>
  <div class="table-scroll owner-table-scroll" data-testid="data-table-scroll" tabindex="0" role="region" :aria-label="caption">
    <table class="owner-table">
      <caption>{{ caption }}</caption>
      <thead>
        <tr><th v-for="c in columns" :key="c.key" scope="col">{{ c.label }}</th></tr>
      </thead>
      <tbody>
        <tr v-for="(row, i) in rows" :key="i">
          <td v-for="c in columns" :key="c.key">{{ row[c.key] ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

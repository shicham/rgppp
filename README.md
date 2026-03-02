# Scripts SQL - Job Purge RGPD

Scripts pour tester et valider le job de purge RGPD **avant** et **après** son exécution, et générer un rapport.

## Paramètre commun

- **mois_anonymisation** : nombre de mois (négatif). Ex. `-24` = bénéficiaires anonymisés il y a plus de 24 mois (éligibles à la purge).
- À adapter en tête de chaque script ou via `DEFINE mois_anonymisation = -24` en SQL*Plus.

## Fichiers

| Fichier | Usage |
|---------|--------|
| `job_purge_test_avant.sql` | À exécuter **avant** le lancement du job. Donne les comptages par table pour les données qui seront supprimées. |
| `job_purge_test_apres.sql` | À exécuter **après** le job. Vérifie qu’il ne reste plus de lignes éligibles (tous les comptages attendus à 0). |
| `job_purge_rapport.sql` | Génère **un seul jeu de résultats** (LIBELLE + NOMBRE) pour export CSV/Excel et comparaison avant/après. |

## Procédure recommandée

1. **Avant le job**
   - Exécuter `job_purge_test_avant.sql` (ou `job_purge_rapport.sql`) avec le même `mois_anonymisation` que le job.
   - Exporter le résultat du rapport : `rapport_avant_YYYYMMDD.csv`.

2. **Lancer le job de purge** (batch RGPD).

3. **Après le job**
   - Exécuter `job_purge_test_apres.sql` pour vérifier que tous les comptages sont à 0.
   - Ou exécuter à nouveau `job_purge_rapport.sql` et exporter : `rapport_apres_YYYYMMDD.csv`.

4. **Comparaison**
   - Comparer `rapport_avant` et `rapport_apres` : les lignes concernées par la purge doivent passer à 0 dans `rapport_apres`.

## Exécution (Oracle)

- **SQL*Plus** : `sqlplus user/pass@db @job_purge_rapport.sql`
- **SQL Developer** : ouvrir le script, définir la variable `mois_anonymisation` si demandé, exécuter (F5).

Pour changer la valeur du paramètre dans le script, modifier la ligne :
```sql
DEFINE mois_anonymisation = -24
```

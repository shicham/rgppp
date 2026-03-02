-- =============================================================================
-- JOB PURGE RGPD - REQUÊTES DE TEST AVANT LANCEMENT
-- =============================================================================
-- Exécuter ce script AVANT de lancer le job de purge pour capturer l'état
-- des données qui seront supprimées.
--
-- Paramètre à définir (SQL*Plus / SQL Developer) :
--   DEFINE mois_anonymisation = -24
-- (ex: -24 = bénéficiaires anonymisés il y a plus de 24 mois)
-- =============================================================================

-- Définir le paramètre si non défini (adapter la valeur selon l'environnement)
DEFINE mois_anonymisation = -24

PROMPT ========== RAPPORT AVANT LANCEMENT JOB PURGE ==========
PROMPT Date exécution: 
SELECT TO_CHAR(SYSDATE, 'DD/MM/YYYY HH24:MI:SS') AS date_rapport FROM DUAL;
PROMPT Paramètre mois_anonymisation: &mois_anonymisation
PROMPT

-- -----------------------------------------------------------------------------
-- 1. Bénéficiaires éligibles à la purge (même critère que le job)
-- -----------------------------------------------------------------------------
PROMPT --- 1. Bénéficiaires éligibles à la purge ---
SELECT COUNT(*) AS nb_beneficiaires_a_purger
FROM BENEFICIAIRE
WHERE DATE_ANONYMISATION IS NOT NULL
  AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation);

-- Liste des IDs (pour trace / vérification manuelle, limiter en prod)
-- SELECT ID, NIR, DATE_ANONYMISATION
-- FROM BENEFICIAIRE
-- WHERE DATE_ANONYMISATION IS NOT NULL
--   AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
--   AND ROWNUM <= 100;

-- -----------------------------------------------------------------------------
-- 2. Tables de liaison - nombre de lignes concernées
-- -----------------------------------------------------------------------------
PROMPT --- 2. Tables de liaison ---

SELECT COUNT(*) AS nb_lien_regul_benef_declaration
FROM LIEN_REGUL_BENEF_DECLARATION lrbd
WHERE lrbd.ID_REGULARISATION IN (
  SELECT ID FROM REGULARISATION_BENEFICIAIRE
  WHERE ID_BENEFICIAIRE IN (
    SELECT ID FROM BENEFICIAIRE
    WHERE DATE_ANONYMISATION IS NOT NULL
      AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
  )
);

SELECT COUNT(*) AS nb_lien_versement_declaration
FROM LIEN_VERSEMENT_DECLARATION lvd
WHERE lvd.ID_VERSEMENT IN (
  SELECT ID FROM VERSEMENT
  WHERE ID_BENEFICIAIRE IN (
    SELECT ID FROM BENEFICIAIRE
    WHERE DATE_ANONYMISATION IS NOT NULL
      AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
  )
);

SELECT COUNT(*) AS nb_lien_regul_vers_declaration
FROM LIEN_REGUL_VERS_DECLARATION lrvd
WHERE lrvd.ID_REGULARISATION IN (
  SELECT RV.ID FROM REGULARISATION_VERSEMENT RV
  JOIN VERSEMENT V ON V.ID = RV.ID_VERSEMENT
  WHERE V.ID_BENEFICIAIRE IN (
    SELECT ID FROM BENEFICIAIRE
    WHERE DATE_ANONYMISATION IS NOT NULL
      AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
  )
);

SELECT COUNT(*) AS nb_lien_benef_declaration
FROM LIEN_BENEF_DECLARATION
WHERE ID_BENEFICIAIRE IN (
  SELECT ID FROM BENEFICIAIRE
  WHERE DATE_ANONYMISATION IS NOT NULL
    AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
);

-- -----------------------------------------------------------------------------
-- 3. Contribution sociale (RGUR, REGL, RGUB)
-- -----------------------------------------------------------------------------
PROMPT --- 3. Contribution sociale ---

SELECT COUNT(*) AS nb_contrib_rgur
FROM CONTRIBUTION_SOCIALE
WHERE CODE_TYPE_PAIEMENT = 'RGUR'
  AND ID_PAIEMENT IN (
    SELECT RV.ID FROM REGULARISATION_VERSEMENT RV
    JOIN VERSEMENT V ON V.ID = RV.ID_VERSEMENT
    WHERE V.ID_BENEFICIAIRE IN (
      SELECT ID FROM BENEFICIAIRE
      WHERE DATE_ANONYMISATION IS NOT NULL
        AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
    )
  );

SELECT COUNT(*) AS nb_contrib_regl
FROM CONTRIBUTION_SOCIALE
WHERE CODE_TYPE_PAIEMENT = 'REGL'
  AND ID_PAIEMENT IN (
    SELECT ID FROM VERSEMENT
    WHERE ID_BENEFICIAIRE IN (
      SELECT ID FROM BENEFICIAIRE
      WHERE DATE_ANONYMISATION IS NOT NULL
        AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
    )
  );

SELECT COUNT(*) AS nb_contrib_rgub
FROM CONTRIBUTION_SOCIALE
WHERE CODE_TYPE_PAIEMENT = 'RGUB'
  AND ID_PAIEMENT IN (
    SELECT ID FROM REGULARISATION_BENEFICIAIRE
    WHERE ID_BENEFICIAIRE IN (
      SELECT ID FROM BENEFICIAIRE
      WHERE DATE_ANONYMISATION IS NOT NULL
        AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
    )
  );

-- -----------------------------------------------------------------------------
-- 4. Historiques bénéficiaire
-- -----------------------------------------------------------------------------
PROMPT --- 4. Historiques bénéficiaire ---

SELECT COUNT(*) AS nb_histo_taux_beneficiaire
FROM HISTO_TAUX_BENEFICIAIRE
WHERE ID_BENEFICIAIRE IN (
  SELECT ID FROM BENEFICIAIRE
  WHERE DATE_ANONYMISATION IS NOT NULL
    AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
);

SELECT COUNT(*) AS nb_histo_beneficiaire
FROM HISTO_BENEFICIAIRE
WHERE ID_BENEFICIAIRE IN (
  SELECT ID FROM BENEFICIAIRE
  WHERE DATE_ANONYMISATION IS NOT NULL
    AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
);

SELECT COUNT(*) AS nb_declaration_amorcage_taux
FROM DECLARATION_AMORCAGE_TAUX
WHERE ID_BENEFICIAIRE IN (
  SELECT ID FROM BENEFICIAIRE
  WHERE DATE_ANONYMISATION IS NOT NULL
    AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
);

-- -----------------------------------------------------------------------------
-- 5. Versements et régularisations
-- -----------------------------------------------------------------------------
PROMPT --- 5. Versements et régularisations ---

SELECT COUNT(*) AS nb_regularisation_versement
FROM REGULARISATION_VERSEMENT
WHERE ID_VERSEMENT IN (
  SELECT ID FROM VERSEMENT
  WHERE ID_BENEFICIAIRE IN (
    SELECT ID FROM BENEFICIAIRE
    WHERE DATE_ANONYMISATION IS NOT NULL
      AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
  )
);

SELECT COUNT(*) AS nb_versement
FROM VERSEMENT
WHERE ID_BENEFICIAIRE IN (
  SELECT ID FROM BENEFICIAIRE
  WHERE DATE_ANONYMISATION IS NOT NULL
    AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
);

SELECT COUNT(*) AS nb_regularisation_beneficiaire
FROM REGULARISATION_BENEFICIAIRE
WHERE ID_BENEFICIAIRE IN (
  SELECT ID FROM BENEFICIAIRE
  WHERE DATE_ANONYMISATION IS NOT NULL
    AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation)
);

-- -----------------------------------------------------------------------------
-- 6. Individus (NIR null = non partagés, supprimés par le job)
-- -----------------------------------------------------------------------------
PROMPT --- 6. Individus (NIR null) ---

SELECT COUNT(*) AS nb_histo_taux_individu
FROM HISTO_TAUX_INDIVIDU
WHERE ID_INDIVIDU IN (SELECT ID FROM INDIVIDU WHERE NIR IS NULL);

SELECT COUNT(*) AS nb_histo_donnees_individu
FROM HISTO_DONNEES_INDIVIDU
WHERE ID_INDIVIDU IN (SELECT ID FROM INDIVIDU WHERE NIR IS NULL);

SELECT COUNT(*) AS nb_individu_nir_null
FROM INDIVIDU
WHERE NIR IS NULL;

PROMPT
PROMPT ========== FIN RAPPORT AVANT LANCEMENT ==========

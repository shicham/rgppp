-- =============================================================================
-- JOB PURGE RGPD - REQUÊTES DE TEST APRÈS LANCEMENT
-- =============================================================================
-- Exécuter ce script APRÈS le job de purge pour vérifier que les données
-- éligibles ont bien été supprimées (tous les comptages doivent être à 0
-- pour les tables liées aux bénéficiaires purgés).
--
-- Utiliser le MÊME paramètre mois_anonymisation que pour le test avant.
--   DEFINE mois_anonymisation = -24
-- =============================================================================

DEFINE mois_anonymisation = -24

PROMPT ========== RAPPORT APRÈS LANCEMENT JOB PURGE ==========
PROMPT Date exécution: 
SELECT TO_CHAR(SYSDATE, 'DD/MM/YYYY HH24:MI:SS') AS date_rapport FROM DUAL;
PROMPT Paramètre mois_anonymisation: &mois_anonymisation
PROMPT

-- -----------------------------------------------------------------------------
-- 1. Bénéficiaires encore éligibles à la purge (doit être 0 après job réussi)
-- -----------------------------------------------------------------------------
PROMPT --- 1. Bénéficiaires éligibles restants (attendu: 0) ---
SELECT COUNT(*) AS nb_beneficiaires_eligibles_restants
FROM BENEFICIAIRE
WHERE DATE_ANONYMISATION IS NOT NULL
  AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, &mois_anonymisation);

-- -----------------------------------------------------------------------------
-- 2. Tables de liaison - vérification résidus (tous attendus à 0)
-- -----------------------------------------------------------------------------
PROMPT --- 2. Tables de liaison (attendus: 0) ---

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
-- 3. Contribution sociale (attendus: 0)
-- -----------------------------------------------------------------------------
PROMPT --- 3. Contribution sociale (attendus: 0) ---

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
-- 4. Historiques bénéficiaire (attendus: 0)
-- -----------------------------------------------------------------------------
PROMPT --- 4. Historiques bénéficiaire (attendus: 0) ---

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
-- 5. Versements et régularisations (attendus: 0)
-- -----------------------------------------------------------------------------
PROMPT --- 5. Versements et régularisations (attendus: 0) ---

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
-- 6. Individus NIR null (peut être 0 ou résidu selon cas)
-- -----------------------------------------------------------------------------
PROMPT --- 6. Individus NIR null restants ---

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
PROMPT ========== FIN RAPPORT APRÈS LANCEMENT ==========
PROMPT Si tous les comptages ci-dessus sont à 0, la purge est cohérente.

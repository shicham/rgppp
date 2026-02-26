package com.groupama.pasrau.batch.metier;

import com.groupama.pasrau.batch.model.BeneficiaireAAnonymiser;
import com.groupama.pasrau.model.entities.LienBenefDeclaration;
import java.util.List;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class PurgeRepositoryCustomImpl implements PurgeRepositoryCustom {

    /** Taille des sous-lots pour les DELETE (évite ORA-00060 deadlock avec gros IN). */
    private static final int DELETE_BATCH_SIZE = 100;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Exécute une suppression par lots pour limiter la taille des IN et réduire les deadlocks Oracle.
     */
    private int executeDeleteInBatches(List<Long> beneficiaireIds, Function<List<Long>, Integer> deleteForBatch) {
        if (beneficiaireIds == null || beneficiaireIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < beneficiaireIds.size(); i += DELETE_BATCH_SIZE) {
            List<Long> batch = beneficiaireIds.subList(i, Math.min(i + DELETE_BATCH_SIZE, beneficiaireIds.size()));
            total += deleteForBatch.apply(batch);
            entityManager.flush();
            entityManager.clear();
        }
        return total;
    }


    @Override
    public List<BeneficiaireAAnonymiser> extractBeneficiaireAnonymiser(Integer moisAnonymisation) {
        return entityManager
            .createNativeQuery("SELECT  ID AS idBeneficiaire, NIR AS nir  FROM BENEFICIAIRE  WHERE DATE_ANONYMISATION is not null AND DATE_ANONYMISATION < ADD_MONTHS(SYSDATE, :moisAnonymisation) ","BeneficiaireAAnonymiserMapping")
            .setParameter("moisAnonymisation", moisAnonymisation)
            .getResultList();
    }
    /* ===============================
       1️⃣ Tables de liaison
       =============================== */

    @Override
    public int deleteLienRegulBenefDeclarationByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                " DELETE FROM LIEN_REGUL_BENEF_DECLARATION " +
                " WHERE ID_REGULARISATION IN ( " +
                " SELECT ID FROM REGULARISATION_BENEFICIAIRE " +
                " WHERE ID_BENEFICIAIRE IN (:ids) " +
                " ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public List<LienBenefDeclaration> lienBenefDeclarationByBeneficiaireIds(List<Long> beneficiaireIds) {
        return entityManager.createQuery(
                "  SELECT d FROM LienBenefDeclaration d " +
                    "  WHERE d.lienBenefDeclarationPk.idBeneficiaire IN (:ids) "
            )
            .setParameter("ids", beneficiaireIds)
            .getResultList();
    }

    @Override
    public int deleteLienBenefDeclarationByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "  DELETE FROM LIEN_BENEF_DECLARATION " +
                "  WHERE ID_BENEFICIAIRE IN (:ids) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteLienVersementDeclarationByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                " DELETE FROM LIEN_VERSEMENT_DECLARATION " +
                "  WHERE ID_VERSEMENT IN ( " +
                " SELECT ID FROM VERSEMENT " +
                "  WHERE ID_BENEFICIAIRE IN (:ids) " +
                "  ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteLienRegulVersDeclarationByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "  DELETE FROM LIEN_REGUL_VERS_DECLARATION " +
                "   WHERE ID_REGULARISATION IN ( " +
                "  SELECT RV.ID " +
                "  FROM REGULARISATION_VERSEMENT RV " +
                "   WHERE RV.ID_VERSEMENT IN ( " +
                "    SELECT ID FROM VERSEMENT " +
                "   WHERE ID_BENEFICIAIRE IN (:ids) " +
                "    ) " +
                "   ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    /* ===============================
       2️⃣ Contribution sociale
       =============================== */

    @Override
    public int deleteContributionSocialeRegulBeneficiaireByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                " DELETE FROM CONTRIBUTION_SOCIALE " +
                "  WHERE CODE_TYPE_PAIEMENT='RGUB' AND ID_PAIEMENT IN ( SELECT ID FROM REGULARISATION_BENEFICIAIRE WHERE   ID_BENEFICIAIRE IN (:ids) ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteContributionSocialeRegulVersementByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                " DELETE FROM CONTRIBUTION_SOCIALE " +
                "  WHERE CODE_TYPE_PAIEMENT='RGUR' AND ID_PAIEMENT IN ( SELECT rv.ID FROM REGULARISATION_VERSEMENT rv join VERSEMENT v on v.ID = rv.ID_VERSEMENT WHERE v.ID_BENEFICIAIRE IN (:ids) ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteContributionSocialeVersementByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                " DELETE FROM CONTRIBUTION_SOCIALE " +
                "  WHERE CODE_TYPE_PAIEMENT='REGL' AND ID_PAIEMENT IN ( SELECT ID FROM VERSEMENT WHERE ID_BENEFICIAIRE IN (:ids) ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }


    /* ===============================
       3️⃣ Historiques bénéficiaire
       =============================== */

    @Override
    public int deleteHistoTauxBeneficiaireByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "   DELETE FROM HISTO_TAUX_BENEFICIAIRE " +
                "  WHERE ID_BENEFICIAIRE IN (:ids) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteHistoBeneficiaireByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "   DELETE FROM HISTO_BENEFICIAIRE " +
                "   WHERE ID_BENEFICIAIRE IN (:ids) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteDeclarationAmorcageTauxByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "   DELETE FROM DECLARATION_AMORCAGE_TAUX " +
                "   WHERE ID_BENEFICIAIRE IN (:ids) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteDeclaration(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                " DELETE FROM DECLARATION WHERE ID IN ( SELECT ID_DECLARATION FROM LIEN_BENEF_DECLARATION WHERE ID_BENEFICIAIRE in (:ids)) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }


    @Override
    public int deleteDeclarationByIds(List<Long> declarationIds) {
        if (declarationIds == null || declarationIds.isEmpty()) {
            return 0;
        }
        return entityManager.createQuery(
                " DELETE FROM Declaration WHERE id IN (:ids) and id not in (select ID_DECLARATION from LIEN_BENEF_DECLARATION )"
            )
            .setParameter("ids", declarationIds)
            .executeUpdate();
    }

    /* ===============================
       4️⃣ Versements
       =============================== */

    @Override
    public int deleteRegularisationVersementByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "  DELETE FROM REGULARISATION_VERSEMENT " +
                "   WHERE ID_VERSEMENT IN ( " +
                "    SELECT ID FROM VERSEMENT " +
                "   WHERE ID_BENEFICIAIRE IN (:ids) " +
                "     ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteVersementByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "  DELETE FROM VERSEMENT " +
                "  WHERE ID_BENEFICIAIRE IN (:ids) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    /* ===============================
       5️⃣ Régularisation bénéficiaire
       =============================== */

    @Override
    public int deleteRegularisationBeneficiaireByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "  DELETE FROM REGULARISATION_BENEFICIAIRE " +
                "   WHERE ID_BENEFICIAIRE IN (:ids) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    /* ===============================
       6️⃣ Historiques individu
       =============================== */

    @Override
    public int deleteHistoTauxIndividu() {
        return entityManager.createNativeQuery(
            "  DELETE FROM  HISTO_TAUX_INDIVIDU  WHERE ID_INDIVIDU IN (  "
                + "SELECT I.ID  FROM INDIVIDU I  WHERE I.NIR is null  "
                + ") "
                )
            .executeUpdate();
    }

    @Override
    public int deleteHistoDonneesIndividu() {
        return entityManager.createNativeQuery(
            "  DELETE FROM HISTO_DONNEES_INDIVIDU  WHERE ID_INDIVIDU IN (   SELECT I.ID    FROM INDIVIDU I WHERE I.NIR is null  )"
                )
            .executeUpdate();
    }

    /* ===============================
       7️⃣ Individu non partagé
       =============================== */

    @Override
    public int deleteIndividu() {
        return entityManager.createNativeQuery(
            "  DELETE FROM INDIVIDU WHERE nir is null "
                )
            .executeUpdate();
    }

    /* ===============================
       8️⃣ Suppression finale
       =============================== */

    @Override
    public int deleteBeneficiaireByIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "   DELETE FROM BENEFICIAIRE " +
                "   WHERE ID IN (:ids) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

}

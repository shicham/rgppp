package com.groupama.pasrau.batch.metier;

import com.groupama.pasrau.batch.model.BeneficiaireAAnonymiser;
import com.groupama.pasrau.model.entities.LienBenefDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class PurgeRepositoryCustomImpl implements PurgeRepositoryCustom {

    /** Taille des sous-lots pour les DELETE (évite ORA-00060 deadlock avec gros IN). */
    private static final int DELETE_BATCH_SIZE = 50;

    /** Limite Oracle pour les listes IN (ORA-01795). */
    private static final int ORACLE_IN_MAX = 1000;

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
                " DELETE FROM LIEN_REGUL_BENEF_DECLARATION lr " +
                " WHERE EXISTS ( " +
                "   SELECT 1 FROM REGULARISATION_BENEFICIAIRE rb " +
                "   WHERE rb.ID = lr.ID_REGULARISATION AND rb.ID_BENEFICIAIRE IN (:ids) " +
                " ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public List<LienBenefDeclaration> lienBenefDeclarationByBeneficiaireIds(List<Long> beneficiaireIds) {
        if (beneficiaireIds == null || beneficiaireIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<LienBenefDeclaration> result = new ArrayList<>();
        for (int i = 0; i < beneficiaireIds.size(); i += ORACLE_IN_MAX) {
            List<Long> batch = beneficiaireIds.subList(i, Math.min(i + ORACLE_IN_MAX, beneficiaireIds.size()));
            result.addAll(entityManager.createQuery(
                    "  SELECT d FROM LienBenefDeclaration d " +
                    "  WHERE d.lienBenefDeclarationPk.idBeneficiaire IN (:ids) "
                )
                .setParameter("ids", batch)
                .getResultList());
        }
        return result;
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
                " DELETE FROM LIEN_VERSEMENT_DECLARATION lv " +
                " WHERE EXISTS ( " +
                "   SELECT 1 FROM VERSEMENT v " +
                "   WHERE v.ID = lv.ID_VERSEMENT AND v.ID_BENEFICIAIRE IN (:ids) " +
                " ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteLienRegulVersDeclarationByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "  DELETE FROM LIEN_REGUL_VERS_DECLARATION lr " +
                "  WHERE EXISTS ( " +
                "    SELECT 1 FROM REGULARISATION_VERSEMENT rv " +
                "    JOIN VERSEMENT v ON v.ID = rv.ID_VERSEMENT " +
                "    WHERE rv.ID = lr.ID_REGULARISATION AND v.ID_BENEFICIAIRE IN (:ids) " +
                "  ) "
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
                " DELETE FROM CONTRIBUTION_SOCIALE cs " +
                " WHERE cs.CODE_TYPE_PAIEMENT = 'RGUB' " +
                " AND EXISTS ( " +
                "   SELECT 1 FROM REGULARISATION_BENEFICIAIRE rb " +
                "   WHERE rb.ID = cs.ID_PAIEMENT AND rb.ID_BENEFICIAIRE IN (:ids) " +
                " ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteContributionSocialeRegulVersementByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                " DELETE FROM CONTRIBUTION_SOCIALE cs " +
                " WHERE cs.CODE_TYPE_PAIEMENT = 'RGUR' " +
                " AND EXISTS ( " +
                "   SELECT 1 FROM REGULARISATION_VERSEMENT rv " +
                "   JOIN VERSEMENT v ON v.ID = rv.ID_VERSEMENT " +
                "   WHERE rv.ID = cs.ID_PAIEMENT AND v.ID_BENEFICIAIRE IN (:ids) " +
                " ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }

    @Override
    public int deleteContributionSocialeVersementByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                " DELETE FROM CONTRIBUTION_SOCIALE cs " +
                " WHERE cs.CODE_TYPE_PAIEMENT = 'REGL' " +
                " AND EXISTS ( " +
                "   SELECT 1 FROM VERSEMENT v " +
                "   WHERE v.ID = cs.ID_PAIEMENT AND v.ID_BENEFICIAIRE IN (:ids) " +
                " ) "
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
                " DELETE FROM DECLARATION d " +
                " WHERE EXISTS ( " +
                "   SELECT 1 FROM LIEN_BENEF_DECLARATION lb " +
                "   WHERE lb.ID_DECLARATION = d.ID AND lb.ID_BENEFICIAIRE IN (:ids) " +
                " ) "
            ).setParameter("ids", batch).executeUpdate()
        );
    }


    @Override
    public int deleteDeclarationByIds(List<Long> declarationIds) {
        if (declarationIds == null || declarationIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < declarationIds.size(); i += ORACLE_IN_MAX) {
            List<Long> batch = declarationIds.subList(i, Math.min(i + ORACLE_IN_MAX, declarationIds.size()));
            total += entityManager.createQuery(
                    " DELETE FROM Declaration d " +
                    " WHERE d.id IN (:ids) " +
                    " AND NOT EXISTS ( " +
                    "   SELECT 1 FROM LienBenefDeclaration lb WHERE lb.lienBenefDeclarationPk.idDeclaration = d.id " +
                    " ) "
                )
                .setParameter("ids", batch)
                .executeUpdate();
        }
        return total;
    }

    /* ===============================
       4️⃣ Versements
       =============================== */

    @Override
    public int deleteRegularisationVersementByBeneficiaireIds(List<Long> beneficiaireIds) {
        return executeDeleteInBatches(beneficiaireIds, batch ->
            entityManager.createNativeQuery(
                "  DELETE FROM REGULARISATION_VERSEMENT rv " +
                "  WHERE EXISTS ( " +
                "    SELECT 1 FROM VERSEMENT v " +
                "    WHERE v.ID = rv.ID_VERSEMENT AND v.ID_BENEFICIAIRE IN (:ids) " +
                "  ) "
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
            "  DELETE FROM HISTO_TAUX_INDIVIDU h " +
            "  WHERE EXISTS ( " +
            "    SELECT 1 FROM INDIVIDU I WHERE I.ID = h.ID_INDIVIDU AND I.NIR IS NULL " +
            "  ) "
            )
            .executeUpdate();
    }

    @Override
    public int deleteHistoDonneesIndividu() {
        return entityManager.createNativeQuery(
            "  DELETE FROM HISTO_DONNEES_INDIVIDU h " +
            "  WHERE EXISTS ( " +
            "    SELECT 1 FROM INDIVIDU I WHERE I.ID = h.ID_INDIVIDU AND I.NIR IS NULL " +
            "  ) "
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

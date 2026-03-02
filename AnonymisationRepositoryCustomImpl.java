package com.groupama.pasrau.batch.metier;

import com.groupama.pasrau.batch.model.BeneficiaireAAnonymiser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class AnonymisationRepositoryCustomImpl implements AnonymisationRepositoryCustom {

    /** Limite Oracle pour les listes IN (ORA-01795). */
    private static final int ORACLE_IN_MAX = 1000;

    @PersistenceContext
    private EntityManager entityManager;


    @Override
    public List<BeneficiaireAAnonymiser> extractBeneficiaireAAnonymis(Integer moisRetentionCreation,
        Integer moisRetentionRegularisation, Integer moisRetentionVersement,
        Integer moisRetentionDeclaration) {
        return entityManager
            .createNativeQuery("SELECT " +
                "  B.ID AS idBeneficiaire, B.NIR AS nir " +
                "FROM BENEFICIAIRE B " +
                "LEFT JOIN ( " +
                "  SELECT N.ID_BENEFICIAIRE, MAX(A.DATE_ENVOI) AS DATE_ENVOI " +
                "  FROM LIEN_BENEF_DECLARATION N " +
                "  JOIN DECLARATION A ON N.ID_DECLARATION = A.ID " +
                "  GROUP BY N.ID_BENEFICIAIRE " +
                ") D ON B.ID = D.ID_BENEFICIAIRE " +
                "LEFT JOIN ( " +
                "  SELECT E.ID_BENEFICIAIRE, MAX(E.DATE_VERSEMENT) AS DATE_VERSEMENT " +
                "  FROM VERSEMENT E " +
                "  GROUP BY E.ID_BENEFICIAIRE " +
                ") C ON B.ID = C.ID_BENEFICIAIRE " +
                "LEFT JOIN ( " +
                "  SELECT V.ID_BENEFICIAIRE, MAX(RV.DATE_REGULARISATION) AS DATE_REGULARISATION " +
                "  FROM VERSEMENT V JOIN REGULARISATION_VERSEMENT RV ON V.ID = RV.ID_VERSEMENT " +
                "  GROUP BY V.ID_BENEFICIAIRE " +
                ") F ON B.ID = F.ID_BENEFICIAIRE " +
                "WHERE B.DATE_ANONYMISATION IS NULL AND (D.DATE_ENVOI <= ADD_MONTHS(SYSDATE, :moisRetentionDeclaration) OR D.DATE_ENVOI IS NULL) "
                +
                "AND B.DATE_CREATION < ADD_MONTHS(SYSDATE, :moisRetentionCreation) " +
                "AND (C.DATE_VERSEMENT <= ADD_MONTHS(SYSDATE, :moisRetentionVersement) OR C.DATE_VERSEMENT IS NULL) "
                +
                "AND (F.DATE_REGULARISATION <= ADD_MONTHS(SYSDATE, :moisRetentionRegularisation) OR F.DATE_REGULARISATION IS NULL)",
                "BeneficiaireAAnonymiserMapping")
            .setParameter("moisRetentionCreation", moisRetentionCreation)
            .setParameter("moisRetentionVersement", moisRetentionVersement)
            .setParameter("moisRetentionRegularisation", moisRetentionRegularisation)
            .setParameter("moisRetentionDeclaration", moisRetentionDeclaration)
            .getResultList();
    }


    @Override
    public List<BeneficiaireAAnonymiser> findBenefAnonymise(LocalDateTime  dateAnonym) {
        LocalDateTime startOfDay = dateAnonym.minusSeconds(10l);
        LocalDateTime endOfDay = dateAnonym.plusSeconds(3l);

        return entityManager
            .createNativeQuery(
                "select ID AS idBeneficiaire, NIR AS nir from Beneficiaire where  "
                    + "  date_anonymisation >= :startOfDay and  date_anonymisation < :endOfDay ",
                "BeneficiaireAAnonymiserMapping" )
            .setParameter("startOfDay", startOfDay)
            .setParameter("endOfDay", endOfDay)
            .getResultList();
    }


    @Override
    public List<String> findNirElegible(List<String> nirs) {
        if (nirs == null || nirs.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < nirs.size(); i += ORACLE_IN_MAX) {
            List<String> batch = nirs.subList(i, Math.min(i + ORACLE_IN_MAX, nirs.size()));
            result.addAll(entityManager
                .createNativeQuery(
                    "SELECT NIR FROM BENEFICIAIRE WHERE DATE_ANONYMISATION IS NULL AND NIR IN (:nirs)"
                )
                .setParameter("nirs", batch)
                .getResultList());
        }
        return result;
    }


    @Override
    public List<Long> findIndividuRgpd(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += ORACLE_IN_MAX) {
            List<Long> batch = ids.subList(i, Math.min(i + ORACLE_IN_MAX, ids.size()));
            result.addAll(entityManager
                .createNativeQuery(
                    " SELECT a.ID FROM INDIVIDU a " +
                    " INNER JOIN BENEFICIAIRE c ON c.NIR = a.NIR AND c.ID IN (:ids) " +
                    " WHERE NOT EXISTS ( " +
                    "   SELECT 1 FROM BENEFICIAIRE b " +
                    "   WHERE b.NIR = a.NIR AND b.ID <> c.ID AND b.DATE_ANONYMISATION IS NULL " +
                    " ) "
                )
                .setParameter("ids", batch)
                .getResultList());
        }
        return result;
    }

    @Override
    public List<Long> findIndividuRgpdNyNir(List<String> nirs) {
        if (nirs == null || nirs.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < nirs.size(); i += ORACLE_IN_MAX) {
            List<String> batch = nirs.subList(i, Math.min(i + ORACLE_IN_MAX, nirs.size()));
            result.addAll(entityManager
                .createNativeQuery("SELECT ID FROM INDIVIDU WHERE NIR IN (:nirs)")
                .setParameter("nirs", batch)
                .getResultList());
        }
        return result;
    }

    @Override
    public int anonymiserBenefeciareNir(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < ids.size(); i += ORACLE_IN_MAX) {
            List<Long> batch = ids.subList(i, Math.min(i + ORACLE_IN_MAX, ids.size()));
            total += entityManager
                .createNativeQuery(
                    "UPDATE BENEFICIAIRE SET NIR = NULL WHERE ID IN (:ids) AND DATE_ANONYMISATION IS NOT NULL"
                )
                .setParameter("ids", batch)
                .executeUpdate();
        }
        return total;
    }
    // ---------- UPDATES ----------

    @Override
    public int anonymisationHistoDonneesIndividu(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < ids.size(); i += ORACLE_IN_MAX) {
            List<Long> batch = ids.subList(i, Math.min(i + ORACLE_IN_MAX, ids.size()));
            total += entityManager
                .createNativeQuery(
                    "UPDATE HISTO_DONNEES_INDIVIDU SET NIR = NULL, NOM_FAMILLE = NULL, NOM_USAGE = NULL, PRENOMS = NULL, "
                        + "DATE_NAISSANCE = NULL, LIEU_NAISSANCE = NULL, DATE_CREATION = NULL, DEPARTEMENT_NAISSANCE = NULL, CODE_PAYS_NAISSANCE = NULL, CODE_SEXE = NULL "
                        + "WHERE ID_INDIVIDU IN (:ids)")
                .setParameter("ids", batch)
                .executeUpdate();
        }
        return total;
    }


    @Override
    public int anonymisationIndividu(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < ids.size(); i += ORACLE_IN_MAX) {
            List<Long> batch = ids.subList(i, Math.min(i + ORACLE_IN_MAX, ids.size()));
            total += entityManager
                .createNativeQuery(
                    "UPDATE INDIVIDU SET " +
                    "NIR = NULL, NOM_FAMILLE = NULL, NOM_USAGE = NULL, PRENOMS = NULL, " +
                    "DATE_NAISSANCE = NULL, LIEU_NAISSANCE = NULL, " +
                    "DEPARTEMENT_NAISSANCE = NULL, CODE_PAYS_NAISSANCE = NULL, " +
                    "CODE_SEXE = NULL, DATE_RECEPTION_TAUX = NULL " +
                    "WHERE ID IN (:ids)")
                .setParameter("ids", batch)
                .executeUpdate();
        }
        return total;
    }


    @Override
    public int anonymisationBeneficiaire(List<Long> ids, LocalDateTime dateAnonimisation) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < ids.size(); i += ORACLE_IN_MAX) {
            List<Long> batch = ids.subList(i, Math.min(i + ORACLE_IN_MAX, ids.size()));
            total += entityManager
                .createNativeQuery(
                    "UPDATE BENEFICIAIRE SET " +
                    "NOM_FAMILLE = 'Xxxxx', NOM_USAGE = 'Xxxxx', PRENOMS = 'Xxxxx', " +
                    "ADRESSE = NULL, CODE_POSTAL = NULL, LOCALITE = NULL, " +
                    "CODE_DISTRI_ETR = NULL, COMPL_LOCALISATION = NULL, " +
                    "DATE_NAISSANCE = NULL, LIEU_NAISSANCE = NULL, NTT = NULL, " +
                    "DEPARTEMENT_NAISSANCE = NULL, CODE_PAYS_NAISSANCE = NULL, CODE_SEXE = NULL, DATE_ANONYMISATION = :dateAnonimisation " +
                    "WHERE ID IN (:ids)")
                .setParameter("ids", batch)
                .setParameter("dateAnonimisation", dateAnonimisation)
                .executeUpdate();
        }
        return total;
    }

    @Override
    public int anonymisationHistoriqueBeneficiaire(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < ids.size(); i += ORACLE_IN_MAX) {
            List<Long> batch = ids.subList(i, Math.min(i + ORACLE_IN_MAX, ids.size()));
            total += entityManager
                .createNativeQuery(
                    "UPDATE HISTO_BENEFICIAIRE SET " +
                    "NIR = NULL, NOM_FAMILLE = NULL, NOM_USAGE = NULL, PRENOMS = NULL, " +
                    "ADRESSE = NULL, CODE_POSTAL = NULL, LOCALITE = NULL, CODE_PAYS = NULL, " +
                    "SERV_DISTRI = NULL, CODE_DISTRI_ETR = NULL, COMPL_LOCALISATION = NULL, " +
                    "DATE_NAISSANCE = NULL, LIEU_NAISSANCE = NULL, NTT = NULL, " +
                    "DEPARTEMENT_NAISSANCE = NULL, CODE_PAYS_NAISSANCE = NULL, CODE_SEXE = NULL " +
                    "WHERE ID_BENEFICIAIRE IN (:ids)")
                .setParameter("ids", batch)
                .executeUpdate();
        }
        return total;
    }
}

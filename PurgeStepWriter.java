package com.groupama.pasrau.batch.job.step.purge;

import com.groupama.pasrau.batch.config.ApplicationRgpdConfig;
import com.groupama.pasrau.batch.utils.JobConstants;
import com.groupama.pasrau.batch.utils.PurgeReportUtils;
import com.groupama.pasrau.batch.metier.PurgeRepositoryCustom;
import com.groupama.pasrau.batch.model.BeneficiaireAAnonymiser;
import com.groupama.pasrau.metier.domain.DeclarationRepository;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class PurgeStepWriter implements ItemWriter<BeneficiaireAAnonymiser> {

    private static final Logger log = LoggerFactory.getLogger(PurgeStepWriter.class);

    private final PurgeRepositoryCustom purgeRepositoryCustom;
    private final DeclarationRepository declarationRepository;
    @Autowired
    private ApplicationRgpdConfig applicationRgpdConfig;
    @Value("#{jobExecutionContext['" + JobConstants.I_DATE_EXECUTION_JOB + "']}")
    private LocalDateTime dateExecutionJob;

    public PurgeStepWriter(PurgeRepositoryCustom purgeRepositoryCustom, DeclarationRepository declarationRepository) {
        this.purgeRepositoryCustom = purgeRepositoryCustom;
        this.declarationRepository = declarationRepository;
    }
   /**
     * Écrit les bénéficiaires à supprimer dans la base de données et met à jour le compte rendu technique.
     *
     * @param items la liste des bénéficiaires à supprimer
     */
    @Override
    public void write(List<? extends BeneficiaireAAnonymiser> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        String filePath = applicationRgpdConfig.getCheminFichierOutputCompteRenduTechnique(dateExecutionJob);
        File file = new File(filePath);

        List<Long> beneficiaireIds = items.stream()
            .map(BeneficiaireAAnonymiser::getIdBeneficiaire)
            .collect(Collectors.toList());

        PurgeReportUtils.writeLine(file, String.format("  ✔ BENEFICIAIRE A SUPPRIMER IDS : %s", beneficiaireIds));
    /* ===============================
       1️⃣ Tables de liaison
       =============================== */

        int  deleteLienRegulBenefDeclarationByBeneficiaireIds = purgeRepositoryCustom.deleteLienRegulBenefDeclarationByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteLienRegulBenefDeclarationByBeneficiaireIds", deleteLienRegulBenefDeclarationByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "LIEN_REGUL_BENEF_DECLARATION", deleteLienRegulBenefDeclarationByBeneficiaireIds);

        int  deleteLienVersementDeclarationByBeneficiaireIds = purgeRepositoryCustom.deleteLienVersementDeclarationByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteLienVersementDeclarationByBeneficiaireIds", deleteLienVersementDeclarationByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "LIEN_VERSEMENT_DECLARATION", deleteLienVersementDeclarationByBeneficiaireIds);

        int  deleteContributionSocialeRegulVersementByBeneficiaireIds = purgeRepositoryCustom.deleteContributionSocialeRegulVersementByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteContributionSocialeRegulVersementByBeneficiaireIds", deleteContributionSocialeRegulVersementByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "CONTRIBUTION_SOCIALE CODE_TYPE_PAIEMENT='RGUR'", deleteContributionSocialeRegulVersementByBeneficiaireIds);

        int  deleteLienRegulVersDeclarationByBeneficiaireIds = purgeRepositoryCustom.deleteLienRegulVersDeclarationByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteLienRegulVersDeclarationByBeneficiaireIds", deleteLienRegulVersDeclarationByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "LIEN_REGUL_VERS_DECLARATION", deleteLienRegulVersDeclarationByBeneficiaireIds);
    /* ===============================
       2️⃣ Contribution sociale
       =============================== */


    /* ===============================
       3️⃣ Historiques bénéficiaire
       =============================== */

        int  deleteHistoTauxBeneficiaireByBeneficiaireIds = purgeRepositoryCustom.deleteHistoTauxBeneficiaireByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteHistoTauxBeneficiaireByBeneficiaireIds", deleteHistoTauxBeneficiaireByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "HISTO_TAUX_BENEFICIAIRE", deleteHistoTauxBeneficiaireByBeneficiaireIds);

        int  deleteHistoBeneficiaireByBeneficiaireIds = purgeRepositoryCustom.deleteHistoBeneficiaireByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteHistoBeneficiaireByBeneficiaireIds", deleteHistoBeneficiaireByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "HISTO_BENEFICIAIRE", deleteHistoBeneficiaireByBeneficiaireIds);

        int  deleteDeclarationAmorcageTauxByBeneficiaireIds = purgeRepositoryCustom.deleteDeclarationAmorcageTauxByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteDeclarationAmorcageTauxByBeneficiaireIds", deleteDeclarationAmorcageTauxByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "DECLARATION_AMORCAGE_TAUX", deleteDeclarationAmorcageTauxByBeneficiaireIds);

    /* ===============================
       4️⃣ Versements
       =============================== */

        int  deleteRegularisationVersementByBeneficiaireIds = purgeRepositoryCustom.deleteRegularisationVersementByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteRegularisationVersementByBeneficiaireIds", deleteRegularisationVersementByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "REGULARISATION_VERSEMENT", deleteRegularisationVersementByBeneficiaireIds);

        int  deleteContributionSocialeVersementByBeneficiaireIds = purgeRepositoryCustom.deleteContributionSocialeVersementByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteContributionSocialeVersementByBeneficiaireIds", deleteContributionSocialeVersementByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "CONTRIBUTION_SOCIALE='REGL'", deleteContributionSocialeVersementByBeneficiaireIds);

        int  deleteVersementByBeneficiaireIds = purgeRepositoryCustom.deleteVersementByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteVersementByBeneficiaireIds", deleteVersementByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "VERSEMENT", deleteVersementByBeneficiaireIds);

    /* ===============================
       5️⃣ Régularisation bénéficiaire
       =============================== */

        int  deleteContributionSocialeRegulBeneficiaireByBeneficiaireIds = purgeRepositoryCustom.deleteContributionSocialeRegulBeneficiaireByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteContributionSocialeRegulBeneficiaireByBeneficiaireIds", deleteContributionSocialeRegulBeneficiaireByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "CONTRIBUTION_SOCIALE='RGUB'", deleteContributionSocialeRegulBeneficiaireByBeneficiaireIds);

        int  deleteRegularisationBeneficiaireByBeneficiaireIds = purgeRepositoryCustom.deleteRegularisationBeneficiaireByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteRegularisationBeneficiaireByBeneficiaireIds", deleteRegularisationBeneficiaireByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "REGULARISATION_BENEFICIAIRE", deleteRegularisationBeneficiaireByBeneficiaireIds);

    /* ===============================
       6️⃣ Historiques individu
       =============================== */

        int  deleteHistoTauxIndividuByBeneficiaireIds = purgeRepositoryCustom.deleteHistoTauxIndividu();
        log.info("  ✔ {} deleteHistoTauxIndividuByBeneficiaireIds", deleteHistoTauxIndividuByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "HISTO_TAUX_INDIVIDU", deleteHistoTauxIndividuByBeneficiaireIds);

        int  deleteHistoDonneesIndividuByBeneficiaireIds = purgeRepositoryCustom.deleteHistoDonneesIndividu();
        log.info("  ✔ {} deleteHistoDonneesIndividuByBeneficiaireIds", deleteHistoDonneesIndividuByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "HISTO_DONNEES_INDIVIDU", deleteHistoDonneesIndividuByBeneficiaireIds);
    /* ===============================
       7️⃣ Individu (non partagé)
       =============================== */

        int  deleteIndividuIfNotSharedByBeneficiaireIds = purgeRepositoryCustom.deleteIndividu();
        log.info("  ✔ {} deleteIndividuIfNotSharedByBeneficiaireIds", deleteIndividuIfNotSharedByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "INDIVIDU", deleteIndividuIfNotSharedByBeneficiaireIds);

        /* ===============================
       5️⃣ Declaration bénéficiaire
       =============================== */

        int  deleteLienBenefDeclarationByBeneficiaireIds = purgeRepositoryCustom.deleteLienBenefDeclarationByBeneficiaireIds(beneficiaireIds);
        log.info("  ✔ {} deleteLienBenefDeclarationByBeneficiaireIds", deleteLienBenefDeclarationByBeneficiaireIds);
        PurgeReportUtils.writePurgeLine(file, "LIEN_BENEF_DECLARATION", deleteLienBenefDeclarationByBeneficiaireIds);

         /* ===============================
       8️⃣ Suppression finale
       =============================== */

        int  deleteBeneficiaireByIds = purgeRepositoryCustom.deleteBeneficiaireByIds(beneficiaireIds);
        log.info("  ✔ {} deleteBeneficiaireByIds", deleteBeneficiaireByIds);
        PurgeReportUtils.writePurgeLine(file, "BENEFICIAIRE", deleteBeneficiaireByIds);

    }
}

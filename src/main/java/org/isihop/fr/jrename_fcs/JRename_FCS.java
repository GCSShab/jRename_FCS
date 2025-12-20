package org.isihop.fr.jrename_fcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tondeur-h
 */
public class JRename_FCS {

    String dburl = "";
    String dblogin = "";
    String dbpassword = "";
    List<String> srcPathList;
    String srcPathStrList = "";
    String dstPath = "";
    String extension = "";
    String pattern = "";
    String action = "move_lock";

    //database
    Connection conn;
    Statement stmt;

    
    //logs
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(JRename_FCS.class);


    public static void main(String[] args) {new JRename_FCS();}

    /************************
     * Constructeur
     ************************/
    public JRename_FCS() 
    {
        logger.info("=============================");
        //lire le fichier des properties
        lire_properties();
        //Splitter les chemins des dossiers sources
        srcPathList = new ArrayList<>(Arrays.asList(srcPathStrList.split(",")));

        connect_db(); //connecter DB 
        lister_fichiers();
        logger.info("Traitement des fichiers termine...");
        close_db(); //fermer db
    }

    /**
     * *******************************
     * Lire les properties du fichier properties local.
     * *******************************
     */
    private void lire_properties() {
        String currentPath = System.getProperty("user.dir");
        String programName = this.getClass().getSimpleName();
        FileInputStream is = null;

        try {
            is = new FileInputStream(currentPath + "/" + programName + ".properties");
            Properties p = new Properties();
            try {
                //charger le fichier properties
                p.load(is);
                //lecture des variables
                dburl = p.getProperty("dburl", "jdbc:postgresql://vm296.ch-v.net:5432/cmf");
                dblogin = p.getProperty("dblogin", "postgres");
                dbpassword = p.getProperty("dbpassword", "password");
                srcPathStrList = p.getProperty("srcpathlist", "e:/echanges/cmf/");
                dstPath = p.getProperty("dstpath", "e:/echanges/cmf/kaluza");
                extension = p.getProperty("extension", "fcs");
                pattern = p.getProperty("pattern", "\\b\\d{12}\\b");
                action = p.getProperty("action", "move_lock"); //delete, move_lock, delete_preserve
            } catch (IOException ex) {
                logger.error(ex.getMessage());
            }
            logger.info(p.toString()); //log des paramètres 
            } catch (FileNotFoundException ex) {
                logger.error(ex.getMessage());
            } finally {
            try {
                is.close();
            } catch (IOException ex) {
                logger.error(ex.getMessage());
            }
        }
    }

    /**
     * ****************************
     * Lister tous les fichiers CSV du dossier local fichiers
     * ****************************
     */
    private void lister_fichiers() {
        int traitement_Retour=0;
        
        logger.info("Lister les fichiers de la source...");
        
        for (String srcPath : srcPathList) 
        {
            File[] fileInDir=null;
          
                fileInDir = new File(srcPath).listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".fcs"));
          if (fileInDir==null)
          {
                        logger.error("Acces dossier source ou UNC source impossible.");
                        System.exit(0);
          }
          logger.info("Liste des fichiers : "+Arrays.toString(fileInDir));
          
            for (File find : fileInDir) 
            {
                try //pour chaque fichier du dossier source
                {
                    String filePath = find.getAbsolutePath();
                    String fileExtenstion = filePath.substring(filePath.lastIndexOf(".") + 1, filePath.length());
                    if (extension.equals(fileExtenstion)) //tester si est de type fcs
                    {
                        traitement_Retour=traiter_fichiers(filePath);
                        logger.info(filePath+" => traitement => "+ (traitement_Retour==1 ? "OK":"KO"));
                    }

                    //Traitement des fichiers sources selon option action
                    switch (action.toUpperCase()) 
                    {
                        case "MOVE_LOCK" -> setLockExtension(find,"lock");
                        case "DELETE" -> supprimer_Source(find);
                        case "DELETE_PRESERVE" -> supprimer_Preserver_Source(find,traitement_Retour);
                    }
                } catch (IOException ex) {
                    logger.error(ex.getMessage());
                }
            }
        }
    }

    /**
     * ******************************
     * Renommer les fichiers
     *******************************
     */
    private int traiter_fichiers(String fichier) 
    {
        String numechantillon = "";
        String nouveauNom = "";
        //lire le nom et récupérer le numero d'échantillon
        String fichier_reduit = new File(fichier).getName().trim();
        logger.info("traitement du fichier => "+fichier_reduit);
        
        numechantillon = extraire_numechantillon(fichier_reduit);
        logger.info("Numero d'échantillon => "+numechantillon);
        
        //rechercher en base les données correspondantes
        nouveauNom = rechercher_donnees(numechantillon, fichier_reduit);
        logger.info("Nouveau nom pour le fichier => "+nouveauNom);
        //renommer le fichier
        renommer_et_copier_fichier(fichier, nouveauNom, dstPath);
        return (nouveauNom+".fcs").compareTo(fichier_reduit); //valeur de comparaison, indique si renommage ok ou non
    }

    /**
     * **************************
     * Connecter la DB Si non possible pas de traitement
     *
     * @return boolean
     ***************************
     */
    private boolean connect_db() 
    {
        boolean isconnected = false;
        logger.info("Tentative connexion BDD org.postgresql.Driver => "+dburl+"@"+dblogin+"/"+dbpassword);
        
        try {
            Class.forName("org.postgresql.Driver");

            conn = DriverManager.getConnection(dburl, dblogin, dbpassword);
            isconnected = true;
            stmt = conn.createStatement();
            logger.info("Connexion BDD OK...");
            
        } catch (ClassNotFoundException | SQLException ex) {
            logger.error(ex.getMessage());
        }
        return isconnected;
    }

    /**
     * *******************************
     * Fermer la database si possible.
     ********************************
     */
    private void close_db() 
    {
        try 
        {
            stmt.close();
            conn.close();
            logger.info("Fermeture de la base de données.");
        } catch (SQLException ex) {
            logger.error(ex.getMessage());
        }
    }

    /**
     * *****************************
     * Verifier présence ligne en DB
     *
     * @param laLigne
     * @return 
     ******************************
     */
    private String rechercher_donnees(String numechantillon, String nom_fichier) {
        String retour = nom_fichier.substring(0, nom_fichier.lastIndexOf("."));
        try {
            String sql = "SELECT object,panel FROM public.patients WHERE echantillon='" + numechantillon + "'";
            logger.info(sql);
            
            try (ResultSet rs = stmt.executeQuery(sql)) {
                rs.next();
                retour = retour + "_" + rs.getString(1) + "_" + rs.getString(2);
            }

        } catch (SQLException ex) {
            logger.error("NUM echantillon : "+numechantillon+" => "+ ex.getMessage());
        }

        return retour;
    }

    /**
     * ***************************
     * Extraction du numéro d'échantillon
     *
     * @param fichier
     * @return 
     *******************************
     */
    private String extraire_numechantillon(String fichier) {
        String numEchantillon = "";
        //Format d'echantillon fcs => [251672590001][SCREEN SLP]SCREEN SLP DF1 20250618 1118.fcs
        //rechercher le premier [ + 1 et le premier ]
        //extraite le nom seul avec extension 
        //\\b\\d{12,12}\\b
        Pattern pt = Pattern.compile(pattern);
        Matcher m = pt.matcher(fichier);

        if (m.find()) {
            numEchantillon = fichier.substring(m.start(), m.end());
        }

        return numEchantillon;
    }

    /**
     * *******************************
     * Renommer le fichier avec le nouveau nom
     *
     * @param fichier
     * @param nouveauNom 
     ********************************
     */
    private boolean renommer_et_copier_fichier(String fichiersource, String nouveauNom, String destination) {
        destination = destination + "/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        File fichierSource = new File(fichiersource);
        File dossierDest = new File(destination);
        logger.info("Renommer "+fichiersource+" vers "+destination);
        
        // Vérifie si le fichier source existe
        if (!fichierSource.exists()) {
            logger.error("Le fichier source "+fichierSource.getAbsolutePath()+" n'existe pas!");
            return false;
        }

        // Crée le dossier de destination s'il n'existe pas
        if (!dossierDest.exists()) 
        {
            if (!dossierDest.mkdir()){logger.error("Impossible de creer dossier "+destination);}

        // Crée le chemin complet du nouveau fichier
        File fichierDestination = new File(dossierDest, nouveauNom + "." + extension);

        try {
            Files.copy(fichierSource.toPath(), fichierDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Fichier "+fichiersource+" renomme avec succes !");
            return true;
        } catch (IOException e) {
            logger.error("Erreur lors du renommage : " + e.getMessage());
            return false;
        }
      }
        return false;
    }

    /**
     * *****************************
     * Ecrire un fichier verrou
     *
     * @param find
     * @return 
     ******************************
     */
    private void setLockExtension(File find, String extX) 
    {
        if (find.exists()) 
        {
            String new_name = find.getAbsolutePath() + "_"+extX;
            find.renameTo(new File(new_name));
            logger.info("setLockExtension => Renommer vers => "+new_name);
        }
    }

    /**
     * **************************************
     * Supprimer récursivement les fichiers et dossiers
     *
     * @param chemin
     * @throws IOException 
     ***************************************
     */
    public static void supprimer_Source(File chemin) throws IOException 
    {
        chemin.delete(); //supprime le fichier.
        logger.info("Supprimer Source => "+chemin.getAbsolutePath());
    }

    
    /*************************************
     * Supprimer ou conserver selon le cas.
     * @param find
     * @param traitement_OK 
     *************************************/
    private void supprimer_Preserver_Source(File find, int traitement_OK) {
        if (traitement_OK==0)
        {
            //ne pas supprimer Source, juste mettre extension lock
            setLockExtension(find,"error");
        }
        else
        {
            try {
                //supprimer la source
                supprimer_Source(find);
            } catch (IOException ex) {
                logger.error(ex.getMessage());
            }
        }
    }

}

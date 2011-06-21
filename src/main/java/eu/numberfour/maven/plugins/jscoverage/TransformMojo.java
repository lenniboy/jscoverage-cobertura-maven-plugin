package eu.numberfour.maven.plugins.jscoverage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * <p>
 * This goal will compile jsps for a webapp so that they can be included in a
 * war.
 * </p>
 * 
 * @author <a href="mailto:leonard.ehrenfried@web.de">Leonard Ehrenfried</a>
 * 
 * @goal transform
 * @requiresDependencyResolution compile
 * @description Transforms jscoverage's JSON into Cobertura's XML format
 */
public class TransformMojo extends AbstractMojo {

    /**
     * The Maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    /**
     * File into which to save the output of the transformation.
     * 
     * @parameter default-value="${basedir}/target/cobertura-coverage.xml"
     */
    private String outputFile;

    public void execute() throws MojoExecutionException, MojoFailureException {
        {
            FileWriter fileWriter = null;
            try {
                getLog().info("Transforming jscoverage output to Cobertura's XML format...");
                getLog().info("");
                
                Set<Entry<String, JsonElement>> entries = getJson();
                
                Element packages = getPackages(entries);

                Document doc = getDocument(packages);
                XMLOutputter outputter = getOutputter();
                
                fileWriter = new FileWriter(outputFile);
                fileWriter.write(outputter.outputString(doc));

                getLog().info("Output written to "+outputFile);

            } catch (IOException ex) {
                Logger.getLogger(TransformMojo.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    fileWriter.close();
                } catch (IOException ex) {
                    Logger.getLogger(TransformMojo.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private Set<Entry<String, JsonElement>> getJson() throws JsonIOException, JsonSyntaxException, FileNotFoundException {
        JsonParser parser = new JsonParser();
        Reader reader = new FileReader("/Users/lenni/jscoverage.json");
        JsonObject object = parser.parse(reader).getAsJsonObject();
        Set<Entry<String, JsonElement>> entries = object.entrySet();
        return entries;
    }

    private Element getPackages(Set<Entry<String, JsonElement>> entries) {
        Element packages = new Element("packages");
        Element pkg = new Element("package");
        pkg.setAttribute("branch-rate", "0");
        pkg.setAttribute("complexity", "0.0");
        pkg.setAttribute("name", "application");
        packages.addContent(pkg);
        for (Entry entry : entries) {
            Element clazz = getClass(entry);
            pkg.addContent(clazz);
        }
        return packages;
    }

    private static Element getClass(Entry entry) {

        Element clazz = new Element("class");
        clazz.setAttribute("filename", entry.getKey().toString());

        clazz.setAttribute("branch-rate", "0");
        clazz.setAttribute("complexity", "0.0");
        clazz.setAttribute("name", "application");
        clazz.setAttribute("name", entry.getKey().toString());

        JsonObject obj = (JsonObject) entry.getValue();
        JsonArray lines = (JsonArray) obj.get("coverage");

        //Mutable ints so that they can be passes into the method and just be 
        //incremented there and not be returned
        MutableInt interestingLinesInClass = new MutableInt(0);
        MutableInt hitLinesInClass = new MutableInt(0);
        
        Element linesElm = getLines(lines, hitLinesInClass, interestingLinesInClass);
        clazz.addContent(linesElm);
        
        float lineRate = hitLinesInClass.floatValue() / interestingLinesInClass.intValue();
        clazz.setAttribute("line-rate", Float.toString(lineRate));

        //System.out.println(hitLinesInClass.floatValue() + "/" + lines.size());

        return clazz;
    }

    /**
     * Returns the lines Element for the Class in the JsonArray
     * @param array
     * @param classHits
     * @param interestingLinesInClass
     * @return 
     */
    private static Element getLines(JsonArray array, MutableInt classHits, MutableInt interestingLinesInClass) {
        Element lines = new Element("lines");

        for (int i = 0; i < array.size(); i++) {
            Element line = new Element("line");
            line.setAttribute("branch", "false");
            line.setAttribute("line", Integer.toString(i));

            //null entries mean that the line was a comment or other uninteresting
            //code stuff
            if (!(array.get(i) instanceof JsonNull)) {
                interestingLinesInClass.add(1);

                int hits = array.get(i).getAsInt();
                if (hits > 0) {
                    classHits.add(1);
                }

                line.setAttribute("hits", Integer.toString(hits));
                lines.addContent(line);
            }
        }

        return lines;
    }

    private static Document getDocument(Element packages) {
        Document document = new Document();

        DocType doctype = new DocType("coverage",
                "http://cobertura.sourceforge.net/xml/coverage-03.dtd");
        document.addContent(doctype);

        Element coverage = new Element("coverage");
        document.addContent(coverage);

        coverage.addContent(packages);


        return document;
    }

    private static XMLOutputter getOutputter() {
        Format format = Format.getPrettyFormat();
        XMLOutputter outputter = new XMLOutputter(format);
        return outputter;
    }
}

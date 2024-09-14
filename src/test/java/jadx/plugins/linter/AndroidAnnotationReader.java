package jadx.plugins.linter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import jadx.plugins.linter.xmlbinding.AnnotationType;
import jadx.plugins.linter.xmlbinding.ItemType;
import jadx.plugins.linter.xmlbinding.ObjectFactory;
import jadx.plugins.linter.xmlbinding.RootType;
import jadx.plugins.linter.xmlbinding.ValType;

public class AndroidAnnotationReader {

	private static final Set<String> SET_ANNOTATIONS = Set.of("androidx.annotation.LongDef",
			"androidx.annotation.StringDef", "androidx.annotation.IntDef", "android.annotation.LongDef",
			"android.annotation.StringDef", "android.annotation.IntDef");

	private static final Logger LOG = LoggerFactory.getLogger(AndroidAnnotationReader.class);

	public void processRulesWithConstants(final String zipFilePath, final String xmlDest,
			final String constDestFile, final String androidJarLocation) {
		final Set<String> linterConstants = this.rewriteAnnotationsXml(zipFilePath, xmlDest);
		this.extractLinterConstantValues(linterConstants, androidJarLocation, constDestFile);
		this.sortConstantFile(constDestFile);
	}

	private void extractLinterConstantValues(final Set<String> linterConstants, final String classJarFile, final String constDestFile) {
		if (!linterConstants.isEmpty()) {
			try (final ZipFile zipFile = new ZipFile(classJarFile);
					final FileOutputStream fos = new FileOutputStream(constDestFile)) {
				final Enumeration<? extends ZipEntry> entries = zipFile.entries();
				while (entries.hasMoreElements()) {
					final ZipEntry zipEntry = entries.nextElement();
					if (!zipEntry.isDirectory()) {
						String className = zipEntry.getName();
						if (className.endsWith(".class")) {
							processClass(zipFile.getInputStream(zipEntry), className, linterConstants, fos);
						}
					}
				}
			} catch (final IOException e) {
				LOG.error("Failed to export linter constants", e);
			}
		}
	}

	private Set<String> rewriteAnnotationsXml(final String zipFilePath, final String destFile) {
		final RootType exportedRoot = new RootType();
		try {
			final JAXBContext jaxbContext = JAXBContext.newInstance(RootType.class);
			final Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			final SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			final Schema linterResourcesSchema =
					sf.newSchema(AndroidAnnotationReader.class.getClassLoader().getResource("android-linter.xsd"));
			jaxbUnmarshaller.setSchema(linterResourcesSchema);

			final Set<String> linterConstants =
					readAndroidJar(zipFilePath, exportedRoot, jaxbUnmarshaller);

			if (!exportedRoot.getItem().isEmpty()) {
				final Marshaller jaxbmarshaller = jaxbContext.createMarshaller();
				jaxbmarshaller.setSchema(linterResourcesSchema);
				jaxbmarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
				final ObjectFactory objectFactory = new ObjectFactory();
				final JAXBElement<RootType> newResourcesWrapper = objectFactory.createRoot(exportedRoot);
				final File mergedLinterRules = new File(destFile);
				jaxbmarshaller.marshal(newResourcesWrapper, mergedLinterRules);
				return linterConstants;
			}
		} catch (JAXBException | SAXException e) {
			LOG.error("Failed to export linter rules", e);
		}
		return Collections.emptySet();
	}

	private static Set<String> readAndroidJar(final String zipFilePath, final RootType exportedRoot,
			final Unmarshaller jaxbUnmarshaller) {
		final Set<String> linterConstants = new HashSet<>();
		try (final ZipFile zipFile = new ZipFile(zipFilePath)) {
			final Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				final ZipEntry zipEntry = entries.nextElement();
				if (!zipEntry.isDirectory()) {
					parseAnnotationXml(exportedRoot, jaxbUnmarshaller, linterConstants, zipFile.getInputStream(zipEntry));
				}
			}
		} catch (final IOException e) {
			LOG.error("Failed read jar", e);
		}
		return linterConstants;
	}

	private static void parseAnnotationXml(final RootType exportedRoot,
			final Unmarshaller jaxbUnmarshaller, final Set<String> linterConstants, final InputStream inputStream) {
		try {
			final JAXBElement<RootType> resourcesWrapper =
					jaxbUnmarshaller.unmarshal(new StreamSource(inputStream), RootType.class);
			final RootType root = resourcesWrapper.getValue();
			for (final ItemType item : root.getItem()) {
				List<AnnotationType> exportedAnnotations = null;
				if (!item.getName().contains(") ")) { // filter out member variables and rules for return
					continue;
				}
				for (final AnnotationType annotation : item.getAnnotation()) {
					if (SET_ANNOTATIONS.contains(annotation.getName())
							&& !annotation.getVal().isEmpty()) {
						List<String> consts = null;
						for (final ValType val : annotation.getVal()) {
							if (val.getName().equals("value")) {
								consts = checkConstantList(val);
								break;
							}
						}
						if (consts != null && !consts.isEmpty()) {
							linterConstants.addAll(consts);
							if (exportedAnnotations == null) {
								exportedAnnotations = new ArrayList<>();
							}
							exportedAnnotations.add(annotation);
						}
					}
				}
				if (exportedAnnotations != null) {
					final ItemType exportedItem = new ItemType();
					exportedItem.setName(item.getName());
					exportedItem.setAnnotation(exportedAnnotations);
					exportedRoot.getItem().add(exportedItem);
				}
			}
		} catch (final JAXBException e) {
			LOG.error("Failed to parse annotation xml", e);
		}
	}

	// Filter numeric
	private static List<String> checkConstantList(final ValType val) {
		final String[] consts = val.getVal().substring(1, val.getVal().length() - 1).split(", ");
		final List<String> filteredConstantList = new ArrayList<>();
		boolean hasNumericValues = false;
		for (final String s : consts) {
			if (!org.apache.commons.lang3.StringUtils.isNumeric(s)) {
				filteredConstantList.add(s);
			} else {
				hasNumericValues = true;
			}
		}
		if (hasNumericValues && !filteredConstantList.isEmpty()) { // no need to rewrite empty arrays, we skip them later
			val.setVal("{" + String.join(", ", filteredConstantList) + "}");
		}
		return filteredConstantList;
	}

	private static void processClass(final InputStream in, final String className,
			final Set<String> linterConstants, final FileOutputStream fos) {
		try {
			final ClassReader cr = new ClassReader(in);
			cr.accept(new ClassVisitor(Opcodes.ASM9) {

				@Override
				public FieldVisitor visitField(final int access, final String name, final String descriptor,
						final String signature, final Object value) {
					if (value != null && (access & 24) != 0) {
						final String constRecord =
								className.replace('/', '.').replace('$', '.').replace(".class", ".") + name;
						if (linterConstants.contains(constRecord)
								&& (descriptor.equals("I") || descriptor.equals("J") || descriptor.equals("Ljava/lang/String;"))) {
							try {
								fos.write((constRecord + "=" + value + "\n").getBytes(StandardCharsets.UTF_8));
							} catch (final IOException e) {
								LOG.error("Could not write constant to export file", e);
							}
						}
					}
					return super.visitField(access, name, descriptor, signature, value);
				}
			}, 0);
		} catch (final IOException e) {
			LOG.error("Failed to process class", e);
		}
	}

	public boolean extractAarLinterRules(final String group, final String artifact, final InputStream is,
			final String destDir) {
		File classJar = null;
		File annotationsZip = null;
		try (final ZipInputStream zis = new ZipInputStream(is)) {
			ZipEntry zipEntry = zis.getNextEntry();

			while (zipEntry != null) {
				if (!zipEntry.isDirectory()) {
					String filename = zipEntry.getName();
					if (filename.equals("classes.jar")) {
						classJar = File.createTempFile("classes", "jar");
						Files.copy(zis, classJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
					if (filename.equals("annotations.zip")) {
						annotationsZip = File.createTempFile("annotations", "zip");
						Files.copy(zis, annotationsZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
					if (classJar != null && annotationsZip != null) {
						this.processRulesWithConstants(annotationsZip.getAbsolutePath(),
								destDir + File.separator + group + "_" + artifact + ".xml",
								destDir + File.separator + group + "_" + artifact + ".txt", classJar.getAbsolutePath());
						classJar.delete();
						annotationsZip.delete();
						return true;
					}
				}
				zipEntry = zis.getNextEntry();
			}
			if (classJar != null) {
				classJar.delete();
			}
			if (annotationsZip != null) {
				annotationsZip.delete();
			}
		} catch (final IOException e) {
			LOG.error("Failed to extract linter rules: ", e);
		}
		return false;
	}

	private void sortConstantFile(final String constDestFile) {
		try {
			final Path constFile = Path.of(constDestFile);
			if (!constFile.toFile().exists()) {
				return;
			}
			final List<String> constantLines = Files.readAllLines(constFile);
			if (constantLines.isEmpty()) {
				return;
			}
			Collections.sort(constantLines);
			try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(constDestFile))) {
				for (final String line : constantLines) {
					os.write(line.getBytes(StandardCharsets.UTF_8));
					os.write('\n');
				}
			}
		} catch (final IOException e) {
			LOG.error("Failed to write linter constants: ", e);
		}
	}
}

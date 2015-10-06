package com.psddev.cms.tool.page;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.psddev.cms.db.ImageCrop;
import com.psddev.cms.db.ImageTag;
import com.psddev.cms.db.ImageTextOverlay;
import com.psddev.cms.db.StandardImageSize;
import com.psddev.cms.db.ToolUi;
import com.psddev.cms.tool.FileContentType;
import com.psddev.cms.tool.PageServlet;
import com.psddev.cms.tool.ToolPageContext;
import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.ReferentialText;
import com.psddev.dari.db.State;
import com.psddev.dari.util.AggregateException;
import com.psddev.dari.util.ClassFinder;
import com.psddev.dari.util.ContentTypeValidator;
import com.psddev.dari.util.ImageMetadataMap;
import com.psddev.dari.util.IoUtils;
import com.psddev.dari.util.MultipartRequest;
import com.psddev.dari.util.MultipartRequestFilter;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.RoutingFilter;
import com.psddev.dari.util.Settings;
import com.psddev.dari.util.SparseSet;
import com.psddev.dari.util.StorageItem;
import com.psddev.dari.util.StorageItemFilter;
import com.psddev.dari.util.StorageItemPathGenerator;
import com.psddev.dari.util.StringUtils;
import com.psddev.dari.util.TypeReference;

@RoutingFilter.Path(application = "cms", value = "storageItemField")
public class StorageItemField extends PageServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageItemField.class);

    public static void processField(ToolPageContext page) throws IOException, ServletException {

        HttpServletRequest request = page.getRequest();

        State state = State.getInstance(request.getAttribute("object"));

        ObjectField field = (ObjectField) request.getAttribute("field");

        String inputName = ObjectUtils.firstNonBlank((String) request.getAttribute("inputName"), page.param(String.class, "inputName"));
        String actionName = inputName + ".action";
        String storageName = inputName + ".storage";
        String pathName = inputName + ".path";
        String contentTypeName = inputName + ".contentType";
        String fileParamName = inputName + ".file";
        String urlName = inputName + ".url";
        String dropboxName = inputName + ".dropbox";
        String cropsName = inputName + ".crops.";

        String brightnessName = inputName + ".brightness";
        String contrastName = inputName + ".contrast";
        String flipHName = inputName + ".flipH";
        String flipVName = inputName + ".flipV";
        String grayscaleName = inputName + ".grayscale";
        String invertName = inputName + ".invert";
        String rotateName = inputName + ".rotate";
        String sepiaName = inputName + ".sepia";
        String sharpenName = inputName + ".sharpen";
        String blurName = inputName + ".blur";

        String focusXName = inputName + ".focusX";
        String focusYName = inputName + ".focusY";

        String fieldName = field != null ? field.getInternalName() : page.param(String.class, "fieldName");
        StorageItem fieldValue = null;

        if (state != null) {
            fieldValue = (StorageItem) state.getValue(fieldName);
        } else {
            // handles processing of files uploaded on frontend
            UUID typeId = page.param(UUID.class, "typeId");
            ObjectType type = Query.findById(ObjectType.class, typeId);
            field = type.getField(fieldName);
            state = State.getInstance(ObjectType.getInstance(page.param(UUID.class, "typeId")));
        }

        String storageItemPath = page.param(String.class, pathName);
        if (!StringUtils.isBlank(storageItemPath)) {
            StorageItem newItem = StorageItem.Static.createIn(page.param(storageName));
            newItem.setPath(storageItemPath);
            fieldValue = newItem;
        }

        String metadataFieldName = fieldName + ".metadata";
        String cropsFieldName = fieldName + ".crops";

        String action = page.param(actionName);

        Map<String, Object> fieldValueMetadata = null;
        boolean isFormPost = request.getAttribute("isFormPost") != null ? (Boolean) request.getAttribute("isFormPost") : false;
        if (fieldValue != null && (!isFormPost || "keep".equals(action))) {
            fieldValueMetadata = fieldValue.getMetadata();
        }

        if (fieldValueMetadata == null) {
            fieldValueMetadata = new LinkedHashMap<String, Object>();
        }

        Map<String, Object> edits = (Map<String, Object>) fieldValueMetadata.get("cms.edits");

        if (edits == null) {
            edits = new HashMap<String, Object>();
            fieldValueMetadata.put("cms.edits", edits);
        }

        double brightness = ObjectUtils.to(double.class, edits.get("brightness"));
        double contrast = ObjectUtils.to(double.class, edits.get("contrast"));
        boolean flipH = ObjectUtils.to(boolean.class, edits.get("flipH"));
        boolean flipV = ObjectUtils.to(boolean.class, edits.get("flipV"));
        boolean grayscale = ObjectUtils.to(boolean.class, edits.get("grayscale"));
        boolean invert = ObjectUtils.to(boolean.class, edits.get("invert"));
        int rotate = ObjectUtils.to(int.class, edits.get("rotate"));
        boolean sepia = ObjectUtils.to(boolean.class, edits.get("sepia"));
        int sharpen = ObjectUtils.to(int.class, edits.get("sharpen"));

        List<String> blurs = new ArrayList<String>();
        if (!ObjectUtils.isBlank(edits.get("blur"))) {
            Object blur = edits.get("blur");
            if (blur instanceof String && ObjectUtils.to(String.class, blur).matches("(\\d+x){3}\\d+")) {
                blurs.add(ObjectUtils.to(String.class, blur));
            } else if (blur instanceof List) {
                for (Object blurItem : (List) blur) {
                    String blurValue = ObjectUtils.to(String.class, blurItem);
                    if (blurValue.matches("(\\d+x){3}\\d+")) {
                        blurs.add(blurValue);
                    }
                }
            }
        }

        Map<String, ImageCrop> crops = ObjectUtils.to(new TypeReference<Map<String, ImageCrop>>() {
        }, fieldValueMetadata.get("cms.crops"));
        if (crops == null) {
            // for backward compatibility
            crops = ObjectUtils.to(new TypeReference<Map<String, ImageCrop>>() {
            }, state.getValue(cropsFieldName));
        }
        if (crops == null) {
            crops = new HashMap<String, ImageCrop>();
        }

        crops = new TreeMap<String, ImageCrop>(crops);

        Map<String, StandardImageSize> sizes = new HashMap<String, StandardImageSize>();
        for (StandardImageSize size : StandardImageSize.findAll()) {
            String sizeId = size.getId().toString();
            sizes.put(sizeId, size);
            if (crops.get(sizeId) == null) {
                crops.put(sizeId, new ImageCrop());
            }
        }

        Map<String, Double> focusPoint = ObjectUtils.to(new TypeReference<Map<String, Double>>() {
        }, fieldValueMetadata.get("cms.focus"));

        if (focusPoint == null) {
            focusPoint = new HashMap<String, Double>();
        }

        Class hotSpotClass = ObjectUtils.getClassByName(ImageTag.HOTSPOT_CLASS);
        boolean projectUsingBrightSpotImage = hotSpotClass != null && !ObjectUtils.isBlank(ClassFinder.Static.findClasses(hotSpotClass));

        if (isFormPost) {
            File file = null;

            try {

                StorageItem newItem = null;

                brightness = page.param(double.class, brightnessName);
                contrast = page.param(double.class, contrastName);
                flipH = page.param(boolean.class, flipHName);
                flipV = page.param(boolean.class, flipVName);
                grayscale = page.param(boolean.class, grayscaleName);
                invert = page.param(boolean.class, invertName);
                rotate = page.param(int.class, rotateName);
                sepia = page.param(boolean.class, sepiaName);
                sharpen = page.param(int.class, sharpenName);

                Double focusX = page.paramOrDefault(Double.class, focusXName, null);
                Double focusY = page.paramOrDefault(Double.class, focusYName, null);

                edits = new HashMap<String, Object>();

                if (brightness != 0.0) {
                    edits.put("brightness", brightness);
                }
                if (contrast != 0.0) {
                    edits.put("contrast", contrast);
                }
                if (flipH) {
                    edits.put("flipH", flipH);
                }
                if (flipV) {
                    edits.put("flipV", flipV);
                }
                if (invert) {
                    edits.put("invert", invert);
                }
                if (rotate != 0) {
                    edits.put("rotate", rotate);
                }
                if (grayscale) {
                    edits.put("grayscale", grayscale);
                }
                if (sepia) {
                    edits.put("sepia", sepia);
                }
                if (sharpen != 0) {
                    edits.put("sharpen", sharpen);
                }

                if (!ObjectUtils.isBlank(page.params(String.class, blurName))) {
                    blurs = new ArrayList<String>();
                    for (String blur : page.params(String.class, blurName)) {
                        if (!blurs.contains(blur)) {
                            blurs.add(blur);
                        }
                    }

                    if (blurs.size() == 1) {
                        edits.put("blur", blurs.get(0));
                    } else {
                        edits.put("blur", blurs);
                    }
                }

                fieldValueMetadata.put("cms.edits", edits);

                if ("keep".equals(action)) {
                    if (fieldValue != null) {
                        newItem = fieldValue;
                    } else {
                        newItem = StorageItem.Static.createIn(page.param(storageName));
                        newItem.setPath(page.param(pathName));
                        newItem.setContentType(page.param(contentTypeName));
                    }

                } else if ("newUpload".equals(action)
                        || "dropbox".equals(action)) {
                    String name = null;
                    String fileContentType = null;
                    long fileSize = 0;

                    if ("dropbox".equals(action)) {
                        Map<String, Object> fileData = (Map<String, Object>) ObjectUtils.fromJson(page.param(String.class, dropboxName));

                        if (fileData != null) {
                            file = File.createTempFile("cms.", ".tmp");
                            name = ObjectUtils.to(String.class, fileData.get("name"));
                            fileContentType = ObjectUtils.getContentType(name);
                            fileSize = ObjectUtils.to(long.class, fileData.get("bytes"));

                            try (InputStream fileInput = new URL(ObjectUtils.to(String.class, fileData.get("link"))).openStream()) {
                                try (FileOutputStream fileOutput = new FileOutputStream(file)) {
                                    IoUtils.copy(fileInput, fileOutput);
                                }
                            }
                        }

                        new ContentTypeValidator().validate(file, fileContentType);
                        //TODO: move this somewhere reusable, currently duplicated in StorageItemFilter
                        if (name != null
                                && fileContentType != null) {

                            if (fileSize > 0) {
                                fieldValueMetadata.put("originalFilename", name);

                                newItem = StorageItem.Static.createIn(getStorageSetting(Optional.of(field)));
                                newItem.setPath(createStorageItemPath(state.getLabel(), name));
                                newItem.setContentType(fileContentType);

                                Map<String, List<String>> httpHeaders = new LinkedHashMap<String, List<String>>();
                                httpHeaders.put("Cache-Control", Collections.singletonList("public, max-age=31536000"));
                                httpHeaders.put("Content-Length", Collections.singletonList(String.valueOf(fileSize)));
                                httpHeaders.put("Content-Type", Collections.singletonList(fileContentType));
                                fieldValueMetadata.put("http.headers", httpHeaders);
                            }
                        }

                    } else {
                        newItem = StorageItemFilter.getParameter(request, fileParamName, getStorageSetting(Optional.of(field)));
                    }

                    //TODO: merge newItem metadata with fieldValueMetadata

                } else if ("newUrl".equals(action)) {
                    newItem = StorageItem.Static.createUrl(page.param(urlName));
                }
                
                // Standard sizes.
                for (Iterator<Map.Entry<String, ImageCrop>> i = crops.entrySet().iterator(); i.hasNext();) {
                    Map.Entry<String, ImageCrop> e = i.next();
                    String cropId = e.getKey();
                    double x = page.doubleParam(cropsName + cropId + ".x");
                    double y = page.doubleParam(cropsName + cropId + ".y");
                    double width = page.doubleParam(cropsName + cropId + ".width");
                    double height = page.doubleParam(cropsName + cropId + ".height");
                    String texts = page.param(cropsName + cropId + ".texts");
                    String textSizes = page.param(cropsName + cropId + ".textSizes");
                    String textXs = page.param(cropsName + cropId + ".textXs");
                    String textYs = page.param(cropsName + cropId + ".textYs");
                    String textWidths = page.param(cropsName + cropId + ".textWidths");
                    if (x != 0.0 || y != 0.0 || width != 0.0 || height != 0.0 || !ObjectUtils.isBlank(texts)) {
                        ImageCrop crop = e.getValue();
                        crop.setX(x);
                        crop.setY(y);
                        crop.setWidth(width);
                        crop.setHeight(height);
                        crop.setTexts(texts);
                        crop.setTextSizes(textSizes);
                        crop.setTextXs(textXs);
                        crop.setTextYs(textYs);
                        crop.setTextWidths(textWidths);

                        for (Iterator<ImageTextOverlay> j = crop.getTextOverlays().iterator(); j.hasNext();) {
                            ImageTextOverlay textOverlay = j.next();
                            String text = textOverlay.getText();

                            if (text != null) {
                                StringBuilder cleaned = new StringBuilder();

                                for (Object item : new ReferentialText(text, true)) {
                                    if (item instanceof String) {
                                        cleaned.append((String) item);
                                    }
                                }

                                text = cleaned.toString();

                                if (ObjectUtils.isBlank(text.replaceAll("<[^>]*>", ""))) {
                                    j.remove();

                                } else {
                                    textOverlay.setText(text);
                                }
                            }
                        }

                    } else {
                        i.remove();
                    }
                }
                fieldValueMetadata.put("cms.crops", crops);

                // Set focus point
                if (focusX != null && focusY != null) {
                    focusPoint.put("x", focusX);
                    focusPoint.put("y", focusY);
                }
                fieldValueMetadata.put("cms.focus", focusPoint);

                if (newItem != null) {
                    newItem.setMetadata(fieldValueMetadata);
                }

                if (newItem != null
                        && ("newUpload".equals(action)
                        || "dropbox".equals(action))) {
                    newItem.save();
                }

                state.putValue(fieldName, newItem);

                if (projectUsingBrightSpotImage) {
                    page.include("set/hotSpot.jsp");
                }
                return;

            } finally {
                if (file != null && file.exists()) {
                    file.delete();
                }
            }
        }

        Optional<ObjectField> fieldOptional = Optional.of(field);
        Uploader uploader = Uploader.getUploader(fieldOptional);

        // --- Presentation ---
        page.writeStart("div", "class", "inputSmall");

            if (uploader != null) {
                uploader.writeHtml(page, fieldOptional);
            }

            page.writeStart("div", "class", "fileSelector");

                page.writeStart("select",
                        "class", "toggleable",
                        "data-root", ".inputSmall",
                        "name", page.h(actionName));

                    if (fieldValue != null) {
                        page.writeStart("option",
                                "data-hide", ".fileSelectorItem",
                                "data-show", ".fileSelectorExisting",
                                "value", "keep");
                            page.writeHtml(page.localize(StorageItemField.class, "option.keep"));
                        page.writeEnd();
                    }

                    page.writeStart("option",
                            "data-hide", ".fileSelectorItem",
                            "value", "none");
                        page.writeHtml(page.localize(StorageItemField.class, "option.none"));
                    page.writeEnd();

                    page.writeStart("option",
                            "data-hide", ".fileSelectorItem",
                            "data-show", ".fileSelectorNewUpload",
                            "value", "newUpload",
                            fieldValue == null && field.isRequired() ? " selected" : "");
                        page.writeHtml(page.localize(StorageItemField.class, "option.newUpload"));
                    page.writeEnd();

                    page.writeStart("option",
                            "data-hide", ".fileSelectorItem",
                            "data-show", ".fileSelectorNewUrl",
                            "value", "newUrl");
                        page.writeHtml(page.localize(StorageItemField.class, "option.newUrl"));
                    page.writeEnd();

                    if (!ObjectUtils.isBlank(page.getCmsTool().getDropboxApplicationKey())) {
                        page.writeStart("option",
                                "data-hide", ".fileSelectorItem",
                                "data-show", ".fileSelectorDropbox",
                                "value", "dropbox");
                            page.write("Dropbox");
                        page.writeEnd();
                    }
                page.writeEnd();

                page.writeTag("input",
                        "class", "fileSelectorItem fileSelectorNewUpload " + (uploader != null ? ObjectUtils.firstNonNull(uploader.getClassIdentifier(), "") : ""),
                        "type", "file",
                        "name", page.h(fileParamName),
                        "data-input-name", inputName);

                page.writeTag("input",
                        "class", "fileSelectorItem fileSelectorNewUrl",
                        "type", "text",
                        "name", page.h(urlName));

                if (!ObjectUtils.isBlank(page.getCmsTool().getDropboxApplicationKey())) {
                    page.writeStart("span", "class", "fileSelectorItem fileSelectorDropbox", "style", page.cssString("display", "inline-block", "vertical-align", "bottom"));
                        page.writeTag("input",
                                "type", "dropbox-chooser",
                                "name", page.h(dropboxName),
                                "data-link-type", "direct",
                                "style", page.cssString("visibility", "hidden"));
                    page.writeEnd();

                    page.writeStart("script", "type", "text/javascript");
                        page.writeRaw(
                                "$('.fileSelectorDropbox input').on('DbxChooserSuccess', function(event) {\n"
                                        + "   $(this).val(JSON.stringify(event.originalEvent.files[0]));\n"
                                        + "});"
                        );
                    page.writeEnd();
                }
            page.writeEnd();

            if (fieldValue != null) {
                String contentType = fieldValue.getContentType();

                page.writeStart("div",
                        "class", "fileSelectorItem fileSelectorExisting filePreview");
                    page.writeTag("input", "name", page.h(storageName), "type", "hidden", "value", page.h(fieldValue.getStorage()));
                    page.writeTag("input", "name", page.h(pathName), "type", "hidden", "value", page.h(fieldValue.getPath()));
                    page.writeTag("input", "name", page.h(contentTypeName), "type", "hidden", "value", page.h(contentType));

                    if (field.as(ToolUi.class).getStoragePreviewProcessorApplication() != null) {

                        ToolUi ui = field.as(ToolUi.class);
                        String processorPath = ui.getStoragePreviewProcessorPath();
                        if (processorPath != null) {
                            page.include(RoutingFilter.Static.getApplicationPath(ui.getStoragePreviewProcessorApplication())
                                    + StringUtils.ensureStart(processorPath, "/"));
                        }
                    } else {
                        FileContentType.writeFilePreview(page, state, fieldValue);
                    }
                page.writeEnd();
            }
        page.writeEnd();

        if (projectUsingBrightSpotImage) {
            page.include("set/hotSpot.jsp");
        }
    }

    static String getStorageSetting(Optional<ObjectField> field) {
        String storageSetting = null;

        if (field.isPresent()) {
            String fieldStorageSetting = field.get().as(ToolUi.class).getStorageSetting();
            if (!StringUtils.isBlank(fieldStorageSetting)) {
                storageSetting = Settings.get(String.class, fieldStorageSetting);
            }
        }

        if (StringUtils.isBlank(storageSetting)) {
            storageSetting = Settings.get(String.class, StorageItem.DEFAULT_STORAGE_SETTING);
        }

        return storageSetting;
    }

    /**
     * @deprecated Use {@link com.psddev.dari.util.StorageItemPathGenerator#createPath(String)}
     * default implementation.
     */
    @Deprecated
    public static String createStorageItemPath(String label, String fileName) {

        String extension = "";
        String path = createStoragePathPrefix();

        if (!StringUtils.isBlank(fileName)) {
            int lastDotAt = fileName.indexOf('.');

            if (lastDotAt > -1) {
                extension = fileName.substring(lastDotAt);
                fileName = fileName.substring(0, lastDotAt);

            }
        }

        if (ObjectUtils.isBlank(label)
                || ObjectUtils.to(UUID.class, label) != null) {
            label = fileName;
        }

        if (ObjectUtils.isBlank(label)) {
            label = UUID.randomUUID().toString().replace("-", "");
        }

        path += StringUtils.toNormalized(label);
        path += extension;

        return path;
    }

    /**
     * @deprecated No direct replacement. Path generation logic was moved
     * to {@link StorageItemPathGenerator} default implementations.
     */
    @Deprecated
    static String createStoragePathPrefix() {
        String idString = UUID.randomUUID().toString().replace("-", "");
        StringBuilder pathBuilder = new StringBuilder();

        pathBuilder.append(idString.substring(0, 2));
        pathBuilder.append('/');
        pathBuilder.append(idString.substring(2, 4));
        pathBuilder.append('/');
        pathBuilder.append(idString.substring(4));
        pathBuilder.append('/');

        return pathBuilder.toString();
    }

    /**
     * @deprecated Was moved to a background process after upload.
     */
    @Deprecated
    static void tryExtractMetadata(StorageItem storageItem, Map<String, Object> fieldValueMetadata, Optional<InputStream> optionalStream) {
        if (storageItem == null) {
            return;
        }

        ImageMetadataMap metadata = null;
        InputStream inputStream = null;
        String contentType = storageItem.getContentType();

        try {
            if (!fieldValueMetadata.containsKey("width")
                    && !fieldValueMetadata.containsKey("height")
                    && contentType != null
                    && contentType.startsWith("image/")) {

                inputStream = optionalStream.isPresent() ? optionalStream.get() : storageItem.getData();
                metadata = new ImageMetadataMap(inputStream);
                List<Throwable> errors = metadata.getErrors();

                if (!errors.isEmpty()) {
                    LOGGER.debug("Can't read image metadata", new AggregateException(errors));
                }
            }

        } catch (IOException e) {
            LOGGER.debug("Can't read image metadata", e);
        } finally {
            IoUtils.closeQuietly(inputStream);
        }

        if (metadata != null) {
            fieldValueMetadata.putAll(metadata);
        }
    }

    @Override
    protected String getPermissionId() {
        return null;
    }

    @Override
    protected void doService(ToolPageContext page) throws IOException, ServletException {
        processField(page);
    }
}

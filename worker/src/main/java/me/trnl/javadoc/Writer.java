package me.trnl.javadoc;/*
 * Copyright (c) 2008 - 2012 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.mongodb.*;
import com.thoughtworks.qdox.model.*;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class Writer {

    private DB db;
    private JavaPackage javaPackage;
    private Object _libid;

    public Writer(DB db, JavaPackage javaPackage, Object _libid) {
        this.db = db;
        this.javaPackage = javaPackage;
        this._libid = _libid;
    }

    public void run() {
        System.out.println("--- starting:" + javaPackage.getName());
        db.requestStart();
        writePackage(db, javaPackage, _libid);
        db.requestDone();
        System.out.println("--- ending:" + javaPackage.getName());
    }

    private static void writePackage(DB db, JavaPackage packag, Object _libid) {
        DBCollection packagesCollection = db.getCollection("packages");

        DBObject packageObject = new BasicDBObject();
        packageObject.put("_id", new ObjectId());
        packageObject.put("_lib", _libid);

        if (packag != null) {
            packageObject.put("n", packag.getName());

            for (JavaClass javaClass : packag.getClasses()) {
                writeClass(db, javaClass, packageObject.get("_id"), _libid);
            }
            packagesCollection.insert(packageObject);
        }

    }

    private static void writeClass(DB db, JavaClass clazz, Object _pkgid, Object _libid) {
        DBCollection classesCollection = db.getCollection("classes");

        BasicDBObjectBuilder classBuilder = new BasicDBObjectBuilder();
        classBuilder.add("_id", new ObjectId());
        classBuilder.add("_pkg", _pkgid);
        classBuilder.add("_lib", _libid);
        classBuilder.add("comment", clazz.getComment());
        classBuilder.add("n", clazz.getFullyQualifiedName());
        if (clazz.getParentClass() != null)
            classBuilder.add("parent", clazz.getParentClass().getFullyQualifiedName());

        /* Methods */
        List methods = new ArrayList();
        classBuilder.add("methods", methods);
        for (JavaMethod method : clazz.getMethods()) {
            BasicDBObjectBuilder methodBuilder = new BasicDBObjectBuilder();
            methodBuilder.add("name", method.getName());
            methodBuilder.add("comment", method.getComment());
            methodBuilder.add("source", method.getCodeBlock());
            if (method.getReturnType() != null)
                methodBuilder.add("return", method.getReturnType().getFullyQualifiedName());
             /* Modifiers */
            methodBuilder.add("modifiers", method.getModifiers());


            /* Tags */
            List tags = new ArrayList();
            methodBuilder.add("tags", tags);
            for (DocletTag tag : method.getTags()) {
                BasicDBObjectBuilder tagBuilder = new BasicDBObjectBuilder();
                tagBuilder.add(tag.getName(), tag.getValue());
                tags.add(tagBuilder.get());
            }

            /* Params */
            List params = new ArrayList();
            methodBuilder.add("params", params);
            for (JavaParameter parameter : method.getParameters()) {
                BasicDBObjectBuilder paramBuilder = new BasicDBObjectBuilder();
                paramBuilder.add(parameter.getName(), parameter.getType().getFullyQualifiedName());
                params.add(paramBuilder.get());
            }

            /* Exceptions */
            List exceptions = new ArrayList();
            methodBuilder.add("exceptions", exceptions);
            for (Type exception : method.getExceptions()) {
                exceptions.add(exception.getFullyQualifiedName());
            }

            /* Exceptions */
            List annotations = new ArrayList();
            methodBuilder.add("annotations", annotations);
            for (Annotation annotation : method.getAnnotations()) {
                BasicDBObjectBuilder annotationBuilder = new BasicDBObjectBuilder();
                annotationBuilder.add(annotation.getType().getFullyQualifiedName(), annotation.getNamedParameterMap());
                annotations.add(annotationBuilder.get());
            }

            methods.add(methodBuilder.get());
        }


        classesCollection.insert(classBuilder.get());
    }
}

 package protogen;

 import com.google.protobuf.Descriptors;
 import protogen.generated.People;

 import java.util.List;

 public class Test {
     private static void dump(List<Descriptors.FieldDescriptor> fields) {
         for (Descriptors.FieldDescriptor field : fields) {
             System.out.println(field.getName());
             if (field.isRepeated()) {
                 System.out.println(field.getName() + " is repeated");
                 if (field.getType().equals(Descriptors.FieldDescriptor.Type.MESSAGE)) {
                     System.out.println("repeat of type: " + field.getMessageType().getFullName());
                 } else {
                     System.out.println("repeat of type: " + field.getType());
                 }
             }
             else if (field.getType().equals(Descriptors.FieldDescriptor.Type.MESSAGE)) {
                 System.out.println(field.getMessageType().getFullName());
                 if (field.isMapField()) {
                     for (Descriptors.FieldDescriptor field2 : field.getMessageType().getFields()) {
                         // can only be the value in the map
                         if (field2.getType().equals(Descriptors.FieldDescriptor.Type.MESSAGE)) {
                             System.out.println("\t" + field2.getMessageType().getFullName());
                         }
                     }
                 }
             } else {
                 System.out.println(field.getType());
             }

             if (field.getContainingOneof() != null) {
                 System.out.println("oneof for: " + field.getContainingOneof().getName());
             }
            

         }
     }

     public static void main(String[] args) {
         People.Person p;

         dump(People.Person.getDescriptor().getFields());
         dump(People.Address.getDescriptor().getFields());


     }
 }

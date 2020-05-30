package io.vintra.qa.server;

import io.codearte.jfairy.Fairy;
import io.codearte.jfairy.producer.person.Person;

public class ContactFairy extends ContactDTO
{
    public ContactFairy(Fairy fairy)
    {
        Person person = fairy.person();
        setEmail(person.getEmail());
        setFirstName(person.getFirstName());
        setLastName(person.getLastName());
        setMobile(person.getTelephoneNumber());
        setPhone(person.getTelephoneNumber());
    }

}

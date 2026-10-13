package com.atamanahmet.beamlink.nexus.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "peer_list_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PeerListVersion {

        @Id
        @Column
        private Integer id = 1;
        private Long version = 1L;

}
